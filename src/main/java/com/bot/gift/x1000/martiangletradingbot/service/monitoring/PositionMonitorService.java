package com.bot.gift.x1000.martiangletradingbot.service.monitoring;

import com.bot.gift.x1000.martiangletradingbot.config.StrategyProperties;
import com.bot.gift.x1000.martiangletradingbot.model.entity.Position;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeDirection;
import com.bot.gift.x1000.martiangletradingbot.repository.PositionRepository;
import com.bot.gift.x1000.martiangletradingbot.service.execution.OrderExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Position Monitor Service
 * Monitors open positions every 5 seconds for TP/SL triggers and exit conditions
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PositionMonitorService {

    private final PositionRepository positionRepository;
    private final OrderExecutorService orderExecutorService;
    private final StrategyProperties strategyProperties;

    /**
     * Monitor all active positions every 5 seconds
     */
    @Scheduled(fixedRate = 5000) // 5 seconds
    public void monitorPositions() {
        List<Position> activePositions = positionRepository.findAllActivePositions();

        if (activePositions.isEmpty()) {
            return;
        }

        log.debug("Monitoring {} active positions", activePositions.size());

        for (Position position : activePositions) {
            try {
                monitorPosition(position);
            } catch (Exception e) {
                log.error("Error monitoring position {}: {}", position.getSymbol(), e.getMessage(), e);
            }
        }
    }

    /**
     * Monitor a single position for exit conditions
     */
    private void monitorPosition(Position position) {
        // Update current price (in production, this would come from WebSocket)
        updateCurrentPrice(position);

        // Check stop loss
        if (checkStopLoss(position)) {
            log.warn("Stop loss hit for {}", position.getSymbol());
            orderExecutorService.executeExit(position, 100, "STOP_LOSS");
            return;
        }

        // Check take profit levels
        if (checkTakeProfit1(position)) {
            log.info("TP1 hit for {}", position.getSymbol());
            orderExecutorService.executeExit(position, 50, "TP1_HIT");

            // Move stop loss to breakeven after TP1
            moveStopLossToBreakeven(position);
            position.setTp1Hit(true);
            positionRepository.save(position);
            return;
        }

        if (position.getTp1Hit() && checkTakeProfit2(position)) {
            log.info("TP2 hit for {}", position.getSymbol());
            orderExecutorService.executeExit(position, 60, "TP2_HIT"); // 60% of remaining (30% of original)
            position.setTp2Hit(true);
            positionRepository.save(position);
            return;
        }

        if (position.getTp2Hit() && checkTakeProfit3(position)) {
            log.info("TP3/Trailing stop hit for {}", position.getSymbol());
            orderExecutorService.executeExit(position, 100, "TP3_HIT");
            return;
        }

        // Update trailing stop for TP3 if TP2 hit
        if (position.getTp2Hit()) {
            updateTrailingStop(position);
        }

        // Check time-based exits
        checkTimeBasedExits(position);

        // Check emergency exit conditions
        checkEmergencyExits(position);

        // Save position updates
        positionRepository.save(position);
    }

    /**
     * Update current price from market data
     * TODO: Get from WebSocket in production
     */
    private void updateCurrentPrice(Position position) {
        // Placeholder: In production, get from WebSocket feed
        // For now, use the last known price
        // position.updateCurrentPrice(latestPriceFromWebSocket);
    }

    /**
     * Check if stop loss is hit
     */
    private boolean checkStopLoss(Position position) {
        BigDecimal currentPrice = position.getCurrentPrice();
        BigDecimal stopLoss = position.getCurrentStopLoss();

        if (position.getDirection() == TradeDirection.LONG) {
            return currentPrice.compareTo(stopLoss) <= 0;
        } else {
            return currentPrice.compareTo(stopLoss) >= 0;
        }
    }

    /**
     * Check if TP1 is hit
     */
    private boolean checkTakeProfit1(Position position) {
        if (position.getTp1Hit()) {
            return false;
        }

        BigDecimal currentPrice = position.getCurrentPrice();
        BigDecimal tp1 = position.getTp1Level();

        // Also check time-based TP1
        long minutesHeld = Duration.between(position.getEntryTime(), Instant.now()).toMinutes();
        int tp1TimeMinutes = strategyProperties.getExit().getTakeProfit().getTp1().getTimeBasedMinutes();
        BigDecimal minProfit = strategyProperties.getExit().getTakeProfit().getTp1().getMinProfitPercent();

        boolean priceTp = position.getDirection() == TradeDirection.LONG
            ? currentPrice.compareTo(tp1) >= 0
            : currentPrice.compareTo(tp1) <= 0;

        boolean timeTp = minutesHeld >= tp1TimeMinutes &&
            position.getUnrealizedPnlPercent() != null &&
            position.getUnrealizedPnlPercent().compareTo(minProfit) >= 0;

        return priceTp || timeTp;
    }

    /**
     * Check if TP2 is hit
     */
    private boolean checkTakeProfit2(Position position) {
        if (position.getTp2Hit()) {
            return false;
        }

        BigDecimal currentPrice = position.getCurrentPrice();
        BigDecimal tp2 = position.getTp2Level();

        if (position.getDirection() == TradeDirection.LONG) {
            return currentPrice.compareTo(tp2) >= 0;
        } else {
            return currentPrice.compareTo(tp2) <= 0;
        }
    }

    /**
     * Check if TP3/trailing stop is hit
     */
    private boolean checkTakeProfit3(Position position) {
        BigDecimal currentPrice = position.getCurrentPrice();
        BigDecimal trailingStop = position.getCurrentStopLoss();

        // Trailing stop is the current stop loss after TP2
        return checkStopLoss(position);
    }

    /**
     * Move stop loss to breakeven after TP1
     */
    private void moveStopLossToBreakeven(Position position) {
        BigDecimal breakeven = position.getAverageEntryPrice();
        position.setCurrentStopLoss(breakeven);
        log.info("Moved stop loss to breakeven for {}: {}", position.getSymbol(), breakeven);
    }

    /**
     * Update trailing stop for TP3
     */
    private void updateTrailingStop(Position position) {
        BigDecimal trailingPercent = strategyProperties.getExit().getTakeProfit().getTp3().getTrailingStopPercent();
        BigDecimal highestPrice = position.getHighestPriceReached();

        if (highestPrice == null) {
            return;
        }

        BigDecimal trailingDistance = highestPrice
            .multiply(trailingPercent)
            .divide(BigDecimal.valueOf(100), 8, java.math.RoundingMode.HALF_UP);

        BigDecimal newStopLoss;
        if (position.getDirection() == TradeDirection.LONG) {
            newStopLoss = highestPrice.subtract(trailingDistance);
        } else {
            newStopLoss = highestPrice.add(trailingDistance);
        }

        // Only update if new stop loss is better than current
        boolean shouldUpdate = position.getDirection() == TradeDirection.LONG
            ? newStopLoss.compareTo(position.getCurrentStopLoss()) > 0
            : newStopLoss.compareTo(position.getCurrentStopLoss()) < 0;

        if (shouldUpdate) {
            position.setCurrentStopLoss(newStopLoss);
            log.debug("Updated trailing stop for {}: {}", position.getSymbol(), newStopLoss);
        }
    }

    /**
     * Check time-based exit conditions
     */
    private void checkTimeBasedExits(Position position) {
        long minutesHeld = Duration.between(position.getEntryTime(), Instant.now()).toMinutes();

        int partialCloseMinutes = strategyProperties.getExit().getTimeBased().getPartialCloseNoBreakevenMinutes();
        int forceCloseMinutes = strategyProperties.getExit().getTimeBased().getForceCloseNoBreakevenMinutes();
        int maxHoldMinutes = strategyProperties.getExit().getTimeBased().getMaxHoldMinutes();

        // Check if still in profit
        boolean inProfit = position.getUnrealizedPnl() != null &&
            position.getUnrealizedPnl().compareTo(BigDecimal.ZERO) > 0;

        // Partial close if not at breakeven after 10 minutes
        if (minutesHeld >= partialCloseMinutes && !inProfit && !position.getTp1Hit()) {
            log.warn("Partial close - no breakeven after {} minutes for {}", partialCloseMinutes, position.getSymbol());
            orderExecutorService.executeExit(position, 50, "TIME_PARTIAL_CLOSE");

            // Tighten stop loss
            BigDecimal tightenPercent = strategyProperties.getExit().getStopLoss().getTightenAfter10minPercent();
            BigDecimal newSl = position.getAverageEntryPrice()
                .multiply(BigDecimal.ONE.subtract(tightenPercent.divide(BigDecimal.valueOf(100), 8, java.math.RoundingMode.HALF_UP)));
            position.setCurrentStopLoss(newSl);
            return;
        }

        // Force close if not at breakeven after 20 minutes
        if (minutesHeld >= forceCloseMinutes && !inProfit) {
            log.warn("Force close - no breakeven after {} minutes for {}", forceCloseMinutes, position.getSymbol());
            orderExecutorService.executeExit(position, 100, "TIME_FORCE_CLOSE");
            return;
        }

        // Max holding time
        if (minutesHeld >= maxHoldMinutes) {
            log.info("Max holding time reached for {}: {} minutes", position.getSymbol(), minutesHeld);
            orderExecutorService.executeExit(position, 100, "MAX_HOLD_TIME");
        }
    }

    /**
     * Check emergency exit conditions
     */
    private void checkEmergencyExits(Position position) {
        // TODO: Implement emergency exits:
        // 1. BTC dump/pump >2% in 1 minute
        // 2. EMA21 break with strong volume
        // 3. RSI extreme divergence
        // 4. Volume spike >3x average with rejection
        // 5. Loss reaches -2% (failsafe)

        // Placeholder for now
        BigDecimal maxLoss = strategyProperties.getExit().getEmergency().getMaxLossPercent();
        if (position.getUnrealizedPnlPercent() != null &&
            position.getUnrealizedPnlPercent().compareTo(maxLoss.negate()) <= 0) {
            log.error("EMERGENCY EXIT - Max loss reached for {}: {}%",
                position.getSymbol(), position.getUnrealizedPnlPercent());
            orderExecutorService.executeExit(position, 100, "EMERGENCY_MAX_LOSS");
        }
    }
}
