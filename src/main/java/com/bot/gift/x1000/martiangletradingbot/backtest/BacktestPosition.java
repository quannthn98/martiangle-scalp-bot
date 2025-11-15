package com.bot.gift.x1000.martiangletradingbot.backtest;

import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Backtest Position
 * Tracks a simulated open position during backtesting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestPosition {

    private String symbol;
    private TradeDirection direction;
    private Instant entryTime;

    // Position sizing
    private BigDecimal entryPrice;
    private BigDecimal positionSize; // In base currency (BTC, ETH, etc.)
    private BigDecimal positionSizeUsdt;
    private BigDecimal leverage;

    // Stop loss and take profit
    private BigDecimal stopLoss;
    private BigDecimal currentStopLoss; // Can be moved to breakeven/trailing
    private BigDecimal tp1;
    private BigDecimal tp2;
    private BigDecimal tp3;

    // TP hit tracking
    private boolean tp1Hit;
    private boolean tp2Hit;

    // Remaining position after partial exits
    private BigDecimal remainingPositionPercent;

    // Tracking highest/lowest price for trailing stop
    private BigDecimal highestPriceReached;
    private BigDecimal lowestPriceReached;

    // Execution costs
    private BigDecimal entryFee;
    private BigDecimal entrySlippage;

    /**
     * Calculate current unrealized PnL
     */
    public BigDecimal calculateUnrealizedPnl(BigDecimal currentPrice) {
        if (entryPrice == null || currentPrice == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal priceDiff;
        if (direction == TradeDirection.LONG) {
            priceDiff = currentPrice.subtract(entryPrice);
        } else {
            priceDiff = entryPrice.subtract(currentPrice);
        }

        BigDecimal pnl = priceDiff.multiply(positionSize).multiply(leverage);

        // Account for remaining position percentage after partial exits
        return pnl.multiply(remainingPositionPercent)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate current unrealized PnL percentage
     */
    public BigDecimal calculateUnrealizedPnlPercent(BigDecimal currentPrice) {
        if (entryPrice == null || currentPrice == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal priceDiff;
        if (direction == TradeDirection.LONG) {
            priceDiff = currentPrice.subtract(entryPrice);
        } else {
            priceDiff = entryPrice.subtract(currentPrice);
        }

        return priceDiff.divide(entryPrice, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .multiply(leverage);
    }

    /**
     * Update highest price reached (for trailing stop)
     */
    public void updateHighestPrice(BigDecimal currentPrice) {
        if (direction == TradeDirection.LONG) {
            if (highestPriceReached == null || currentPrice.compareTo(highestPriceReached) > 0) {
                this.highestPriceReached = currentPrice;
            }
        } else {
            if (lowestPriceReached == null || currentPrice.compareTo(lowestPriceReached) < 0) {
                this.lowestPriceReached = currentPrice;
            }
        }
    }

    /**
     * Check if stop loss is hit
     */
    public boolean isStopLossHit(BigDecimal currentPrice) {
        if (currentStopLoss == null) {
            return false;
        }

        if (direction == TradeDirection.LONG) {
            return currentPrice.compareTo(currentStopLoss) <= 0;
        } else {
            return currentPrice.compareTo(currentStopLoss) >= 0;
        }
    }

    /**
     * Check if TP1 is hit
     */
    public boolean isTp1Hit(BigDecimal currentPrice) {
        if (tp1Hit || tp1 == null) {
            return false;
        }

        if (direction == TradeDirection.LONG) {
            return currentPrice.compareTo(tp1) >= 0;
        } else {
            return currentPrice.compareTo(tp1) <= 0;
        }
    }

    /**
     * Check if TP2 is hit
     */
    public boolean isTp2Hit(BigDecimal currentPrice) {
        if (tp2Hit || !tp1Hit || tp2 == null) {
            return false;
        }

        if (direction == TradeDirection.LONG) {
            return currentPrice.compareTo(tp2) >= 0;
        } else {
            return currentPrice.compareTo(tp2) <= 0;
        }
    }

    /**
     * Check if TP3 (trailing stop) is hit
     */
    public boolean isTp3Hit(BigDecimal currentPrice) {
        if (!tp2Hit) {
            return false;
        }

        // TP3 is managed by trailing stop, which is the current stop loss after TP2
        return isStopLossHit(currentPrice);
    }

    /**
     * Move stop loss to breakeven
     */
    public void moveStopLossToBreakeven() {
        this.currentStopLoss = this.entryPrice;
    }

    /**
     * Update trailing stop based on highest/lowest price reached
     */
    public void updateTrailingStop(BigDecimal trailingPercent) {
        if (direction == TradeDirection.LONG && highestPriceReached != null) {
            BigDecimal trailingDistance = highestPriceReached
                .multiply(trailingPercent)
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

            BigDecimal newStop = highestPriceReached.subtract(trailingDistance);

            // Only move stop up, never down
            if (currentStopLoss == null || newStop.compareTo(currentStopLoss) > 0) {
                this.currentStopLoss = newStop;
            }
        } else if (direction == TradeDirection.SHORT && lowestPriceReached != null) {
            BigDecimal trailingDistance = lowestPriceReached
                .multiply(trailingPercent)
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

            BigDecimal newStop = lowestPriceReached.add(trailingDistance);

            // Only move stop down, never up
            if (currentStopLoss == null || newStop.compareTo(currentStopLoss) < 0) {
                this.currentStopLoss = newStop;
            }
        }
    }

    /**
     * Execute partial exit
     */
    public void executePartialExit(int exitPercent) {
        BigDecimal exitPercentDecimal = BigDecimal.valueOf(exitPercent);
        this.remainingPositionPercent = this.remainingPositionPercent.subtract(exitPercentDecimal);
    }
}
