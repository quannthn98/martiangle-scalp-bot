package com.bot.gift.x1000.martiangletradingbot.backtest;

import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Backtest Trade Record
 * Represents a simulated trade in the backtesting engine
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestTrade {

    private String tradeId;
    private String symbol;
    private TradeDirection direction;
    private Instant entryTime;
    private Instant exitTime;

    // Entry details
    private BigDecimal entryPrice;
    private BigDecimal positionSize;
    private BigDecimal leverage;

    // Exit details
    private BigDecimal exitPrice;
    private String exitReason;

    // Stop loss and take profit levels
    private BigDecimal stopLoss;
    @Builder.Default
    private List<BigDecimal> takeProfitLevels = new ArrayList<>();

    // Performance metrics
    private BigDecimal profitLossUsdt;
    private BigDecimal profitLossPercent;
    private BigDecimal fees;
    private BigDecimal slippage;
    private BigDecimal netProfitLoss; // After fees and slippage

    // Execution details
    @Builder.Default
    private List<BigDecimal> partialExitPrices = new ArrayList<>();
    @Builder.Default
    private List<Integer> partialExitPercents = new ArrayList<>();

    // Trade duration
    private Long holdingTimeSeconds;

    /**
     * Calculate holding time in seconds
     */
    public void calculateHoldingTime() {
        if (entryTime != null && exitTime != null) {
            this.holdingTimeSeconds = Duration.between(entryTime, exitTime).getSeconds();
        }
    }

    /**
     * Calculate profit/loss percentage
     */
    public void calculateProfitLossPercent() {
        if (entryPrice != null && exitPrice != null) {
            BigDecimal priceDiff;
            if (direction == TradeDirection.LONG) {
                priceDiff = exitPrice.subtract(entryPrice);
            } else {
                priceDiff = entryPrice.subtract(exitPrice);
            }

            this.profitLossPercent = priceDiff
                .divide(entryPrice, 8, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .multiply(leverage);
        }
    }

    /**
     * Check if trade was profitable
     */
    public boolean isWinner() {
        return netProfitLoss != null && netProfitLoss.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Get R-multiple (profit/loss as multiple of risk)
     */
    public BigDecimal getRMultiple() {
        if (entryPrice == null || stopLoss == null || netProfitLoss == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal riskPerUnit = entryPrice.subtract(stopLoss).abs();
        BigDecimal totalRisk = riskPerUnit.multiply(positionSize);

        if (totalRisk.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return netProfitLoss.divide(totalRisk, 2, java.math.RoundingMode.HALF_UP);
    }
}
