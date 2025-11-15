package com.bot.gift.x1000.martiangletradingbot.indicator;

import com.bot.gift.x1000.martiangletradingbot.model.entity.Candle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Volume Indicator Calculator
 * Analyzes trading volume patterns
 */
@Component
@Slf4j
public class VolumeIndicator {

    private static final int SCALE = 8;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Calculate Volume Moving Average
     *
     * @param candles List of candles
     * @param period MA period
     * @return List of volume MA values
     */
    public List<BigDecimal> calculateVolumeMA(List<Candle> candles, int period) {
        if (candles == null || candles.size() < period) {
            log.warn("Insufficient candles for Volume MA calculation");
            return new ArrayList<>();
        }

        List<BigDecimal> volumeMA = new ArrayList<>();

        // Add nulls for warmup period
        for (int i = 0; i < period - 1; i++) {
            volumeMA.add(null);
        }

        // Calculate MA for remaining values
        for (int i = period - 1; i < candles.size(); i++) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = 0; j < period; j++) {
                sum = sum.add(candles.get(i - j).getVolume());
            }
            BigDecimal ma = sum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);
            volumeMA.add(ma);
        }

        return volumeMA;
    }

    /**
     * Calculate volume ratio (current volume / volume MA)
     *
     * @param currentVolume Current candle volume
     * @param volumeMA Volume moving average
     * @return Volume ratio
     */
    public BigDecimal calculateVolumeRatio(BigDecimal currentVolume, BigDecimal volumeMA) {
        if (volumeMA == null || volumeMA.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }

        return currentVolume.divide(volumeMA, SCALE, ROUNDING_MODE);
    }

    /**
     * Check if volume meets minimum threshold
     */
    public boolean meetsMinimumVolume(BigDecimal volumeRatio, BigDecimal minRatio) {
        return volumeRatio != null && volumeRatio.compareTo(minRatio) >= 0;
    }

    /**
     * Detect volume spike
     *
     * @param currentVolume Current volume
     * @param volumeMA Volume MA
     * @param spikeMultiplier Spike threshold (e.g., 3.0 for 3x average)
     * @return true if volume spike detected
     */
    public boolean isVolumeSpike(BigDecimal currentVolume, BigDecimal volumeMA, BigDecimal spikeMultiplier) {
        if (volumeMA == null || volumeMA.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        BigDecimal ratio = currentVolume.divide(volumeMA, SCALE, ROUNDING_MODE);
        return ratio.compareTo(spikeMultiplier) > 0;
    }

    /**
     * Check if volume is rising
     */
    public boolean isVolumeRising(List<Candle> candles, int lookback) {
        if (candles.size() < lookback + 1) {
            return false;
        }

        int size = candles.size();
        BigDecimal currentVolume = candles.get(size - 1).getVolume();
        BigDecimal previousVolume = candles.get(size - lookback - 1).getVolume();

        return currentVolume.compareTo(previousVolume) > 0;
    }

    /**
     * Calculate volume-weighted average price (VWAP) for a candle
     */
    public BigDecimal calculateVWAP(Candle candle) {
        // VWAP = (High + Low + Close) / 3 * Volume
        BigDecimal typicalPrice = candle.getHigh()
            .add(candle.getLow())
            .add(candle.getClose())
            .divide(BigDecimal.valueOf(3), SCALE, ROUNDING_MODE);

        return typicalPrice.multiply(candle.getVolume());
    }

    /**
     * Check if volume confirms trend
     * Rising price should be accompanied by rising volume
     */
    public boolean volumeConfirmsTrend(List<Candle> candles, int lookback) {
        if (candles.size() < lookback + 1) {
            return false;
        }

        int size = candles.size();
        Candle current = candles.get(size - 1);
        Candle previous = candles.get(size - lookback - 1);

        boolean priceRising = current.getClose().compareTo(previous.getClose()) > 0;
        boolean volumeRising = current.getVolume().compareTo(previous.getVolume()) > 0;

        // Bullish confirmation: rising price + rising volume
        // Bearish confirmation: falling price + rising volume
        return (priceRising && volumeRising) || (!priceRising && volumeRising);
    }

    /**
     * Detect low volume (potential consolidation)
     */
    public boolean isLowVolume(BigDecimal currentVolume, BigDecimal volumeMA, BigDecimal threshold) {
        if (volumeMA == null || volumeMA.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        BigDecimal ratio = currentVolume.divide(volumeMA, SCALE, ROUNDING_MODE);
        return ratio.compareTo(threshold) < 0;
    }

    /**
     * Get latest volume MA value
     */
    public BigDecimal getLatestVolumeMA(List<Candle> candles, int period) {
        List<BigDecimal> volumeMA = calculateVolumeMA(candles, period);
        if (volumeMA.isEmpty()) {
            return null;
        }
        return volumeMA.get(volumeMA.size() - 1);
    }

    /**
     * Calculate average volume over period
     */
    public BigDecimal calculateAverageVolume(List<Candle> candles, int period) {
        if (candles.size() < period) {
            return null;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getVolume());
        }

        return sum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);
    }
}
