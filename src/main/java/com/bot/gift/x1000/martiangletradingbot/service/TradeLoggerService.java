package com.bot.gift.x1000.martiangletradingbot.service;

import com.bot.gift.x1000.martiangletradingbot.model.entity.DailyPerformance;
import com.bot.gift.x1000.martiangletradingbot.model.entity.Trade;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeStatus;
import com.bot.gift.x1000.martiangletradingbot.repository.DailyPerformanceRepository;
import com.bot.gift.x1000.martiangletradingbot.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Trade Logger Service
 * Handles trade persistence and daily performance tracking
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TradeLoggerService {

    private final TradeRepository tradeRepository;
    private final DailyPerformanceRepository dailyPerformanceRepository;

    /**
     * Create a new trade record
     */
    @Transactional
    public Trade createTrade(Trade trade) {
        if (trade.getTradeId() == null || trade.getTradeId().isEmpty()) {
            trade.setTradeId(generateTradeId());
        }

        Trade savedTrade = tradeRepository.save(trade);
        log.info("Created trade: {} for {} {} at {}",
            savedTrade.getTradeId(), savedTrade.getSymbol(),
            savedTrade.getDirection(), savedTrade.getAverageEntryPrice());

        return savedTrade;
    }

    /**
     * Update existing trade
     */
    @Transactional
    public Trade updateTrade(Trade trade) {
        Trade updated = tradeRepository.save(trade);
        log.debug("Updated trade: {}", updated.getTradeId());
        return updated;
    }

    /**
     * Close a trade and update daily performance
     */
    @Transactional
    public void closeTrade(Trade trade) {
        trade.setStatus(TradeStatus.CLOSED);
        trade.calculateHoldingTime();
        trade.calculateProfitLossPercent();

        Trade closedTrade = tradeRepository.save(trade);

        // Update daily performance
        updateDailyPerformance(closedTrade);

        log.info("Closed trade: {} with P/L: ${} ({}%)",
            closedTrade.getTradeId(),
            closedTrade.getProfitLossUsdt(),
            closedTrade.getProfitLossPercent());
    }

    /**
     * Update daily performance metrics
     */
    @Transactional
    public void updateDailyPerformance(Trade closedTrade) {
        LocalDate today = LocalDate.now();
        DailyPerformance performance = dailyPerformanceRepository
            .findByTradingDate(today)
            .orElseGet(() -> initializeDailyPerformance(today));

        // Update trade counts
        performance.setTotalTrades(performance.getTotalTrades() + 1);

        BigDecimal pnl = closedTrade.getProfitLossUsdt();
        if (pnl.compareTo(BigDecimal.ZERO) > 0) {
            performance.setWinningTrades(performance.getWinningTrades() + 1);
            BigDecimal totalProfit = performance.getTotalProfit() != null
                ? performance.getTotalProfit().add(pnl)
                : pnl;
            performance.setTotalProfit(totalProfit);

            // Update largest win
            if (performance.getLargestWin() == null || pnl.compareTo(performance.getLargestWin()) > 0) {
                performance.setLargestWin(pnl);
            }
        } else if (pnl.compareTo(BigDecimal.ZERO) < 0) {
            performance.setLosingTrades(performance.getLosingTrades() + 1);
            BigDecimal totalLoss = performance.getTotalLoss() != null
                ? performance.getTotalLoss().add(pnl)
                : pnl;
            performance.setTotalLoss(totalLoss);

            // Update largest loss
            if (performance.getLargestLoss() == null || pnl.compareTo(performance.getLargestLoss()) < 0) {
                performance.setLargestLoss(pnl);
            }
        }

        // Update net PnL
        BigDecimal netPnl = (performance.getTotalProfit() != null ? performance.getTotalProfit() : BigDecimal.ZERO)
            .add(performance.getTotalLoss() != null ? performance.getTotalLoss() : BigDecimal.ZERO);
        performance.setNetPnl(netPnl);

        // Add fees
        if (closedTrade.getFeesPaid() != null) {
            BigDecimal totalFees = performance.getTotalFees() != null
                ? performance.getTotalFees().add(closedTrade.getFeesPaid())
                : closedTrade.getFeesPaid();
            performance.setTotalFees(totalFees);
        }

        // Recalculate metrics
        performance.recalculateMetrics();

        dailyPerformanceRepository.save(performance);

        log.info("Updated daily performance: {} trades, net P/L: ${}, win rate: {}%",
            performance.getTotalTrades(),
            performance.getNetPnl(),
            performance.getWinRate());
    }

    /**
     * Initialize daily performance for a new day
     */
    private DailyPerformance initializeDailyPerformance(LocalDate date) {
        return DailyPerformance.builder()
            .tradingDate(date)
            .totalTrades(0)
            .winningTrades(0)
            .losingTrades(0)
            .totalProfit(BigDecimal.ZERO)
            .totalLoss(BigDecimal.ZERO)
            .netPnl(BigDecimal.ZERO)
            .totalFees(BigDecimal.ZERO)
            .dailyLimitReached(false)
            .tradingStopped(false)
            .build();
    }

    /**
     * Get today's performance
     */
    public Optional<DailyPerformance> getTodayPerformance() {
        return dailyPerformanceRepository.findByTradingDate(LocalDate.now());
    }

    /**
     * Calculate average win
     */
    public BigDecimal calculateAverageWin(DailyPerformance performance) {
        if (performance.getWinningTrades() == null || performance.getWinningTrades() == 0) {
            return BigDecimal.ZERO;
        }

        return performance.getTotalProfit()
            .divide(BigDecimal.valueOf(performance.getWinningTrades()), 8, RoundingMode.HALF_UP);
    }

    /**
     * Calculate average loss
     */
    public BigDecimal calculateAverageLoss(DailyPerformance performance) {
        if (performance.getLosingTrades() == null || performance.getLosingTrades() == 0) {
            return BigDecimal.ZERO;
        }

        return performance.getTotalLoss()
            .divide(BigDecimal.valueOf(performance.getLosingTrades()), 8, RoundingMode.HALF_UP);
    }

    /**
     * Generate unique trade ID
     */
    private String generateTradeId() {
        return "TRADE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Log trade metrics for analysis
     */
    public void logTradeMetrics(Trade trade) {
        log.info("=== Trade Metrics ===");
        log.info("Trade ID: {}", trade.getTradeId());
        log.info("Symbol: {}", trade.getSymbol());
        log.info("Direction: {}", trade.getDirection());
        log.info("Entry Price: {}", trade.getAverageEntryPrice());
        log.info("Exit Price: {}", trade.getAverageExitPrice());
        log.info("P/L: ${} ({}%)", trade.getProfitLossUsdt(), trade.getProfitLossPercent());
        log.info("Holding Time: {} minutes", trade.getHoldingTimeMinutes());
        log.info("Exit Reason: {}", trade.getExitReason());
        log.info("===================");
    }
}
