package com.bot.gift.x1000.martiangletradingbot.service;

import com.bot.gift.x1000.martiangletradingbot.model.dto.EntrySignal;
import com.bot.gift.x1000.martiangletradingbot.model.dto.TrendAnalysis;
import com.bot.gift.x1000.martiangletradingbot.model.entity.Trade;
import com.bot.gift.x1000.martiangletradingbot.repository.PositionRepository;
import com.bot.gift.x1000.martiangletradingbot.service.execution.OrderExecutorService;
import com.bot.gift.x1000.martiangletradingbot.service.monitoring.MarketConditionAvoidanceService;
import com.bot.gift.x1000.martiangletradingbot.service.notification.NotificationService;
import com.bot.gift.x1000.martiangletradingbot.service.risk.RiskManagerService;
import com.bot.gift.x1000.martiangletradingbot.service.scanner.MarketScannerService;
import com.bot.gift.x1000.martiangletradingbot.service.signal.SignalDetectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Trading Engine Service
 * Main orchestrator that coordinates all trading activities
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TradingEngineService {

    private final MarketScannerService marketScannerService;
    private final SignalDetectorService signalDetectorService;
    private final RiskManagerService riskManagerService;
    private final OrderExecutorService orderExecutorService;
    private final NotificationService notificationService;
    private final PositionRepository positionRepository;
    private final MarketConditionAvoidanceService marketConditionAvoidanceService;

    @Value("${trading.account.balance:10000}")
    private BigDecimal accountBalance;

    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    /**
     * Main trading loop - runs every 30 seconds
     */
    @Scheduled(fixedRate = 30000) // 30 seconds
    public void runTradingCycle() {
        if (!isRunning.get() || isShuttingDown.get()) {
            log.debug("Trading engine paused or shutting down");
            return;
        }

        try {
            log.info("=== Starting Trading Cycle ===");

            // Check market conditions for safety
            MarketConditionAvoidanceService.ConditionCheck conditionCheck =
                marketConditionAvoidanceService.isSafeToTrade();

            if (!conditionCheck.isSafe()) {
                log.warn("Trading paused due to market conditions: {}", conditionCheck.reason());
                return;
            }

            // Check if we can open new trades
            if (!riskManagerService.canOpenNewTrade(accountBalance)) {
                log.warn("Cannot open new trades - risk limits reached");
                return;
            }

            // Get trading pairs
            List<String> tradingPairs = marketScannerService.getTier1Pairs();

            // Scan for trending pairs on H4
            List<TrendAnalysis> h4Trends = marketScannerService.scanH4Trends(tradingPairs);

            log.info("Found {} tradeable pairs on H4", h4Trends.stream().filter(TrendAnalysis::isTradeable).count());

            // For each tradeable H4 trend, check H1 confirmation
            for (TrendAnalysis h4Trend : h4Trends) {
                if (!h4Trend.isTradeable()) {
                    continue;
                }

                // Check H1 trend confirmation
                TrendAnalysis h1Trend = marketScannerService.analyzeH1Trend(h4Trend.getSymbol());

                if (h1Trend == null || !h1Trend.isTradeable()) {
                    log.debug("{}: H1 trend not confirmed", h4Trend.getSymbol());
                    continue;
                }

                // Both H4 and H1 trending - check for entry signal on 1m
                EntrySignal signal = signalDetectorService.detectEntrySignal(
                    h4Trend.getSymbol(), h4Trend, h1Trend
                );

                if (signal != null && signal.isValid()) {
                    log.info("Valid entry signal detected for {}", signal.getSymbol());

                    // Execute entry
                    executeEntry(signal);

                    // Only one trade per cycle to avoid overexposure
                    break;
                }
            }

            log.info("=== Trading Cycle Complete ===");

        } catch (Exception e) {
            log.error("Error in trading cycle: {}", e.getMessage(), e);
            notificationService.notifyError("TradingEngine", e.getMessage());
        }
    }

    /**
     * Execute entry with risk management
     */
    private void executeEntry(EntrySignal signal) {
        try {
            // Double-check symbol-specific market conditions
            MarketConditionAvoidanceService.ConditionCheck symbolCheck =
                marketConditionAvoidanceService.isSymbolSafeToTrade(signal.getSymbol());

            if (!symbolCheck.isSafe()) {
                log.warn("Skipping entry for {} - {}", signal.getSymbol(), symbolCheck.reason());
                return;
            }

            // Calculate position size
            BigDecimal positionSize = riskManagerService.calculatePositionSize(
                accountBalance,
                signal,
                signal.getAtr()
            );

            log.info("Calculated position size: ${} for {}", positionSize, signal.getSymbol());

            // Execute entry order
            Trade trade = orderExecutorService.executeEntry(signal, positionSize);

            // Send notification
            notificationService.notifyTradeEntry(trade);

            log.info("Successfully entered trade: {} for {}", trade.getTradeId(), signal.getSymbol());

        } catch (Exception e) {
            log.error("Failed to execute entry for {}: {}", signal.getSymbol(), e.getMessage(), e);
            notificationService.notifyError("OrderExecution", e.getMessage());
        }
    }

    /**
     * Pause trading engine
     */
    public void pause() {
        log.warn("Trading engine paused");
        isRunning.set(false);
        notificationService.notifyError("TradingEngine", "Trading paused");
    }

    /**
     * Resume trading engine
     */
    public void resume() {
        log.info("Trading engine resumed");
        isRunning.set(true);
    }

    /**
     * Check if engine is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Graceful shutdown - close all positions and stop trading
     */
    @PreDestroy
    public void shutdown() {
        if (isShuttingDown.compareAndSet(false, true)) {
            log.warn("=== Trading Engine Shutting Down ===");
            isRunning.set(false);

            // Close all open positions
            closeAllPositions();

            log.info("=== Trading Engine Shutdown Complete ===");
        }
    }

    /**
     * Close all open positions during shutdown
     */
    private void closeAllPositions() {
        try {
            var activePositions = positionRepository.findAllActivePositions();

            if (activePositions.isEmpty()) {
                log.info("No active positions to close");
                return;
            }

            log.warn("Closing {} active positions...", activePositions.size());

            for (var position : activePositions) {
                try {
                    orderExecutorService.executeExit(position, 100, "SHUTDOWN");
                    log.info("Closed position: {}", position.getSymbol());
                } catch (Exception e) {
                    log.error("Failed to close position {}: {}", position.getSymbol(), e.getMessage());
                }
            }

            notificationService.notifyError("TradingEngine", "All positions closed - shutdown complete");

        } catch (Exception e) {
            log.error("Error closing positions during shutdown: {}", e.getMessage(), e);
        }
    }

    /**
     * Get trading engine status
     */
    public String getStatus() {
        long activePositions = positionRepository.countActivePositions();
        return String.format("Running: %s, Active Positions: %d, Shutting Down: %s",
            isRunning.get(), activePositions, isShuttingDown.get());
    }
}
