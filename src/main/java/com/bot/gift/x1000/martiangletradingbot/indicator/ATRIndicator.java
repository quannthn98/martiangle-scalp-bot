package com.bot.gift.x1000.martiangletradingbot.indicator;

import com.bot.gift.x1000.martiangletradingbot.model.entity.Candle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Average True Range (ATR) Calculator
 * Measures market volatility
 *
 * Formula:
 * TR = max[(High - Low), abs(High - Previous Close), abs(Low - Previous Close)]
 * ATR = EMA of TR over Period
 */
@Component
@Slf4j
public class ATRIndicator {

    private static final int SCALE = 8;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Calculate ATR for a list of candles
     *
     * @param candles List of candles (oldest first)
     * @param period ATR period (typically 14)
     * @return List of ATR values
     */
    public List<BigDecimal> calculate(List<Candle> candles, int period) {
        if (candles == null || candles.size() < period + 1) {
            log.warn("Insufficient candles for ATR calculation. Need at least {}", period + 1);
            return new ArrayList<>();
        }

        List<BigDecimal> atrValues = new ArrayList<>();
        List<BigDecimal> trueRanges = new ArrayList<>();

        // Calculate True Range for each candle
        for (int i = 1; i < candles.size(); i++) {
            BigDecimal tr = calculateTrueRange(candles.get(i), candles.get(i - 1));
            trueRanges.add(tr);
        }

        // First ATR is simple average of first 'period' TRs
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(trueRanges.get(i));
        }
        BigDecimal firstAtr = sum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);

        // Add nulls for warmup period
        for (int i = 0; i < period; i++) {
            atrValues.add(null);
        }
        atrValues.add(firstAtr);

        // Calculate subsequent ATR values using smoothed average
        BigDecimal previousAtr = firstAtr;
        for (int i = period; i < trueRanges.size(); i++) {
            BigDecimal atr = calculateSmoothedATR(previousAtr, trueRanges.get(i), period);
            atrValues.add(atr);
            previousAtr = atr;
        }

        return atrValues;
    }

    /**
     * Get the latest ATR value
     */
    public BigDecimal getLatest(List<Candle> candles, int period) {
        List<BigDecimal> atrValues = calculate(candles, period);
        if (atrValues.isEmpty()) {
            return null;
        }
        return atrValues.get(atrValues.size() - 1);
    }

    /**
     * Calculate True Range for a single candle
     * TR = max[(High - Low), abs(High - Prev Close), abs(Low - Prev Close)]
     */
    public BigDecimal calculateTrueRange(Candle current, Candle previous) {
        BigDecimal highLow = current.getHigh().subtract(current.getLow());
        BigDecimal highPrevClose = current.getHigh().subtract(previous.getClose()).abs();
        BigDecimal lowPrevClose = current.getLow().subtract(previous.getClose()).abs();

        return highLow.max(highPrevClose).max(lowPrevClose);
    }

    /**
     * Check if ATR is spiking (indicating increased volatility)
     *
     * @param currentAtr Current ATR value
     * @param atrValues Recent ATR values
     * @param maPeriod Period for ATR moving average
     * @param spikeThreshold Spike threshold multiplier
     * @return true if ATR is spiking
     */
    public boolean isAtrSpiking(BigDecimal currentAtr, List<BigDecimal> atrValues,
                                int maPeriod, BigDecimal spikeThreshold) {
        if (currentAtr == null || atrValues.size() < maPeriod) {
            return false;
        }

        // Calculate ATR moving average
        List<BigDecimal> recentAtrs = atrValues.subList(
            Math.max(0, atrValues.size() - maPeriod),
            atrValues.size()
        );

        BigDecimal atrMa = recentAtrs.stream()
            .filter(atr -> atr != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(recentAtrs.size()), SCALE, ROUNDING_MODE);

        if (atrMa.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        // Check if current ATR exceeds threshold
        BigDecimal threshold = atrMa.multiply(spikeThreshold);
        return currentAtr.compareTo(threshold) > 0;
    }

    /**
     * Check if ATR is rising (volatility increasing)
     */
    public boolean isAtrRising(List<BigDecimal> atrValues, int lookback) {
        if (atrValues.size() < lookback + 1) {
            return false;
        }

        int size = atrValues.size();
        BigDecimal currentAtr = atrValues.get(size - 1);
        BigDecimal previousAtr = atrValues.get(size - lookback - 1);

        return currentAtr != null && previousAtr != null &&
               currentAtr.compareTo(previousAtr) > 0;
    }

    /**
     * Get ATR-based stop loss distance
     * Typically 1-2 times ATR
     */
    public BigDecimal getStopLossDistance(BigDecimal atr, BigDecimal multiplier) {
        if (atr == null) {
            return null;
        }
        return atr.multiply(multiplier).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate position size adjustment based on ATR
     * Higher ATR = smaller position size
     *
     * @param basePositionSize Base position size
     * @param currentAtr Current ATR
     * @param normalAtr Normal/average ATR
     * @return Adjusted position size
     */
    public BigDecimal adjustPositionSizeByAtr(BigDecimal basePositionSize,
                                              BigDecimal currentAtr,
                                              BigDecimal normalAtr) {
        if (currentAtr == null || normalAtr == null ||
            normalAtr.compareTo(BigDecimal.ZERO) == 0) {
            return basePositionSize;
        }

        // Adjust position size inversely to ATR
        // If ATR is 2x normal, position size is 0.5x
        BigDecimal adjustmentFactor = normalAtr.divide(currentAtr, SCALE, ROUNDING_MODE);

        // Cap adjustment between 0.5x and 1.5x
        adjustmentFactor = adjustmentFactor.max(BigDecimal.valueOf(0.5))
                                          .min(BigDecimal.valueOf(1.5));

        return basePositionSize.multiply(adjustmentFactor)
            .setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate smoothed ATR using Wilder's smoothing
     * ATR = ((Previous ATR × (Period - 1)) + Current TR) / Period
     */
    private BigDecimal calculateSmoothedATR(BigDecimal previousAtr, BigDecimal currentTR, int period) {
        BigDecimal periodMinusOne = BigDecimal.valueOf(period - 1);
        BigDecimal periodBd = BigDecimal.valueOf(period);

        return previousAtr.multiply(periodMinusOne)
            .add(currentTR)
            .divide(periodBd, SCALE, ROUNDING_MODE);
    }
}
