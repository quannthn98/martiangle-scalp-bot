package com.bot.gift.x1000.martiangletradingbot.indicator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Bollinger Bands Indicator Calculator
 * Measures volatility and identifies squeeze conditions
 *
 * Formula:
 * Middle Band = SMA(close, period)
 * Upper Band = Middle Band + (stdDev × standard deviation)
 * Lower Band = Middle Band - (stdDev × standard deviation)
 * Bandwidth = (Upper Band - Lower Band) / Middle Band
 */
@Component
@Slf4j
public class BollingerBandsIndicator {

    private static final int SCALE = 8;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Calculate Bollinger Bands
     *
     * @param prices List of closing prices
     * @param period BB period (typically 20)
     * @param stdDevMultiplier Standard deviation multiplier (typically 2)
     * @return Bollinger Bands result
     */
    public BBResult calculate(List<BigDecimal> prices, int period, int stdDevMultiplier) {
        if (prices == null || prices.size() < period) {
            log.warn("Insufficient prices for Bollinger Bands calculation");
            return new BBResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        List<BigDecimal> middle = new ArrayList<>();
        List<BigDecimal> upper = new ArrayList<>();
        List<BigDecimal> lower = new ArrayList<>();
        List<BigDecimal> bandwidth = new ArrayList<>();

        // Add nulls for warmup period
        for (int i = 0; i < period - 1; i++) {
            middle.add(null);
            upper.add(null);
            lower.add(null);
            bandwidth.add(null);
        }

        // Calculate BB for remaining values
        for (int i = period - 1; i < prices.size(); i++) {
            List<BigDecimal> window = prices.subList(i - period + 1, i + 1);

            // Calculate middle band (SMA)
            BigDecimal sma = calculateSMA(window);
            middle.add(sma);

            // Calculate standard deviation
            BigDecimal stdDev = calculateStandardDeviation(window, sma);

            // Calculate upper and lower bands
            BigDecimal upperBand = sma.add(stdDev.multiply(BigDecimal.valueOf(stdDevMultiplier)));
            BigDecimal lowerBand = sma.subtract(stdDev.multiply(BigDecimal.valueOf(stdDevMultiplier)));
            upper.add(upperBand);
            lower.add(lowerBand);

            // Calculate bandwidth
            if (sma.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal bw = upperBand.subtract(lowerBand)
                    .divide(sma, SCALE, ROUNDING_MODE);
                bandwidth.add(bw);
            } else {
                bandwidth.add(null);
            }
        }

        return new BBResult(upper, middle, lower, bandwidth);
    }

    /**
     * Get latest Bollinger Bands values
     */
    public BBResult getLatest(List<BigDecimal> prices, int period, int stdDevMultiplier) {
        BBResult result = calculate(prices, period, stdDevMultiplier);
        if (result.middle.isEmpty()) {
            return null;
        }

        int lastIndex = result.middle.size() - 1;
        List<BigDecimal> lastUpper = new ArrayList<>();
        List<BigDecimal> lastMiddle = new ArrayList<>();
        List<BigDecimal> lastLower = new ArrayList<>();
        List<BigDecimal> lastBandwidth = new ArrayList<>();

        lastUpper.add(result.upper.get(lastIndex));
        lastMiddle.add(result.middle.get(lastIndex));
        lastLower.add(result.lower.get(lastIndex));
        lastBandwidth.add(result.bandwidth.get(lastIndex));

        return new BBResult(lastUpper, lastMiddle, lastLower, lastBandwidth);
    }

    /**
     * Check if Bollinger Bands are in squeeze (low volatility)
     *
     * @param bandwidth Current bandwidth
     * @param squeezeThreshold Squeeze threshold (e.g., 0.02 for 2%)
     * @return true if in squeeze
     */
    public boolean isInSqueeze(BigDecimal bandwidth, BigDecimal squeezeThreshold) {
        return bandwidth != null && bandwidth.compareTo(squeezeThreshold) < 0;
    }

    /**
     * Check if price is touching upper band (potential overbought)
     */
    public boolean isTouchingUpperBand(BigDecimal price, BigDecimal upperBand, BigDecimal tolerance) {
        if (price == null || upperBand == null) {
            return false;
        }

        BigDecimal distance = upperBand.subtract(price).abs();
        BigDecimal threshold = upperBand.multiply(tolerance);

        return distance.compareTo(threshold) <= 0;
    }

    /**
     * Check if price is touching lower band (potential oversold)
     */
    public boolean isTouchingLowerBand(BigDecimal price, BigDecimal lowerBand, BigDecimal tolerance) {
        if (price == null || lowerBand == null) {
            return false;
        }

        BigDecimal distance = price.subtract(lowerBand).abs();
        BigDecimal threshold = lowerBand.multiply(tolerance);

        return distance.compareTo(threshold) <= 0;
    }

    /**
     * Check if price is within bands
     */
    public boolean isPriceWithinBands(BigDecimal price, BigDecimal upperBand, BigDecimal lowerBand) {
        return price != null && upperBand != null && lowerBand != null &&
               price.compareTo(lowerBand) >= 0 &&
               price.compareTo(upperBand) <= 0;
    }

    /**
     * Check if Bollinger Bands are expanding (volatility increasing)
     */
    public boolean isBandsExpanding(List<BigDecimal> bandwidths, int lookback) {
        if (bandwidths.size() < lookback + 1) {
            return false;
        }

        int size = bandwidths.size();
        BigDecimal currentBandwidth = bandwidths.get(size - 1);
        BigDecimal previousBandwidth = bandwidths.get(size - lookback - 1);

        return currentBandwidth != null && previousBandwidth != null &&
               currentBandwidth.compareTo(previousBandwidth) > 0;
    }

    /**
     * Calculate Simple Moving Average
     */
    private BigDecimal calculateSMA(List<BigDecimal> values) {
        BigDecimal sum = values.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(BigDecimal.valueOf(values.size()), SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate Standard Deviation
     */
    private BigDecimal calculateStandardDeviation(List<BigDecimal> values, BigDecimal mean) {
        // Calculate variance
        BigDecimal variance = values.stream()
            .map(value -> value.subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(values.size()), SCALE, ROUNDING_MODE);

        // Calculate standard deviation (square root of variance)
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()))
            .setScale(SCALE, ROUNDING_MODE);
    }

    @Data
    @AllArgsConstructor
    public static class BBResult {
        private List<BigDecimal> upper;
        private List<BigDecimal> middle;
        private List<BigDecimal> lower;
        private List<BigDecimal> bandwidth;
    }
}
