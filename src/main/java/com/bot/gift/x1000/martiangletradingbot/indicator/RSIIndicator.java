package com.bot.gift.x1000.martiangletradingbot.indicator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Relative Strength Index (RSI) Calculator
 * Formula: RSI = 100 - (100 / (1 + RS))
 * where RS = Average Gain / Average Loss
 */
@Component
@Slf4j
public class RSIIndicator {

    private static final int SCALE = 8;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Calculate RSI for a list of prices
     *
     * @param prices List of closing prices (oldest first)
     * @param period RSI period (typically 14)
     * @return List of RSI values
     */
    public List<BigDecimal> calculate(List<BigDecimal> prices, int period) {
        if (prices == null || prices.size() < period + 1) {
            log.warn("Insufficient data for RSI calculation. Need at least {} prices", period + 1);
            return new ArrayList<>();
        }

        List<BigDecimal> rsiValues = new ArrayList<>();

        // Calculate price changes
        List<BigDecimal> gains = new ArrayList<>();
        List<BigDecimal> losses = new ArrayList<>();

        for (int i = 1; i < prices.size(); i++) {
            BigDecimal change = prices.get(i).subtract(prices.get(i - 1));
            gains.add(change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO);
            losses.add(change.compareTo(BigDecimal.ZERO) < 0 ? change.abs() : BigDecimal.ZERO);
        }

        // First RSI uses simple average
        BigDecimal avgGain = calculateAverage(gains.subList(0, period));
        BigDecimal avgLoss = calculateAverage(losses.subList(0, period));

        // Add nulls for warmup period
        for (int i = 0; i < period; i++) {
            rsiValues.add(null);
        }

        // Calculate first RSI
        BigDecimal firstRsi = calculateRsiValue(avgGain, avgLoss);
        rsiValues.add(firstRsi);

        // Calculate subsequent RSI values using smoothed averages
        for (int i = period; i < gains.size(); i++) {
            avgGain = calculateSmoothedAverage(avgGain, gains.get(i), period);
            avgLoss = calculateSmoothedAverage(avgLoss, losses.get(i), period);
            BigDecimal rsi = calculateRsiValue(avgGain, avgLoss);
            rsiValues.add(rsi);
        }

        return rsiValues;
    }

    /**
     * Get the latest RSI value
     */
    public BigDecimal getLatest(List<BigDecimal> prices, int period) {
        List<BigDecimal> rsiValues = calculate(prices, period);
        if (rsiValues.isEmpty()) {
            return null;
        }
        return rsiValues.get(rsiValues.size() - 1);
    }

    /**
     * Detect bullish divergence
     * Price makes lower low but RSI makes higher low
     *
     * @param prices Recent prices
     * @param rsiValues Recent RSI values
     * @param lookback Number of candles to look back
     * @return true if bullish divergence detected
     */
    public boolean detectBullishDivergence(List<BigDecimal> prices, List<BigDecimal> rsiValues, int lookback) {
        if (prices.size() < lookback || rsiValues.size() < lookback) {
            return false;
        }

        int size = prices.size();
        BigDecimal currentPrice = prices.get(size - 1);
        BigDecimal previousPrice = prices.get(size - lookback);
        BigDecimal currentRsi = rsiValues.get(size - 1);
        BigDecimal previousRsi = rsiValues.get(size - lookback);

        if (currentRsi == null || previousRsi == null) {
            return false;
        }

        // Price makes lower low
        boolean priceLowerLow = currentPrice.compareTo(previousPrice) < 0;
        // RSI makes higher low
        boolean rsiHigherLow = currentRsi.compareTo(previousRsi) > 0;

        return priceLowerLow && rsiHigherLow;
    }

    /**
     * Detect bearish divergence
     * Price makes higher high but RSI makes lower high
     */
    public boolean detectBearishDivergence(List<BigDecimal> prices, List<BigDecimal> rsiValues, int lookback) {
        if (prices.size() < lookback || rsiValues.size() < lookback) {
            return false;
        }

        int size = prices.size();
        BigDecimal currentPrice = prices.get(size - 1);
        BigDecimal previousPrice = prices.get(size - lookback);
        BigDecimal currentRsi = rsiValues.get(size - 1);
        BigDecimal previousRsi = rsiValues.get(size - lookback);

        if (currentRsi == null || previousRsi == null) {
            return false;
        }

        // Price makes higher high
        boolean priceHigherHigh = currentPrice.compareTo(previousPrice) > 0;
        // RSI makes lower high
        boolean rsiLowerHigh = currentRsi.compareTo(previousRsi) < 0;

        return priceHigherHigh && rsiLowerHigh;
    }

    /**
     * Check if RSI is in overbought zone
     */
    public boolean isOverbought(BigDecimal rsi, BigDecimal overboughtLevel) {
        return rsi != null && rsi.compareTo(overboughtLevel) > 0;
    }

    /**
     * Check if RSI is in oversold zone
     */
    public boolean isOversold(BigDecimal rsi, BigDecimal oversoldLevel) {
        return rsi != null && rsi.compareTo(oversoldLevel) < 0;
    }

    /**
     * Check if RSI is in valid range for entry
     */
    public boolean isInRange(BigDecimal rsi, BigDecimal minValue, BigDecimal maxValue) {
        return rsi != null &&
               rsi.compareTo(minValue) >= 0 &&
               rsi.compareTo(maxValue) <= 0;
    }

    /**
     * Calculate simple average
     */
    private BigDecimal calculateAverage(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = values.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(BigDecimal.valueOf(values.size()), SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate smoothed average using Wilder's smoothing
     * Smoothed Average = ((Previous Average × (Period - 1)) + Current Value) / Period
     */
    private BigDecimal calculateSmoothedAverage(BigDecimal previousAvg, BigDecimal currentValue, int period) {
        BigDecimal periodMinusOne = BigDecimal.valueOf(period - 1);
        BigDecimal periodBd = BigDecimal.valueOf(period);

        return previousAvg.multiply(periodMinusOne)
            .add(currentValue)
            .divide(periodBd, SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate RSI value from average gain and loss
     * RSI = 100 - (100 / (1 + RS))
     */
    private BigDecimal calculateRsiValue(BigDecimal avgGain, BigDecimal avgLoss) {
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }

        BigDecimal rs = avgGain.divide(avgLoss, SCALE, ROUNDING_MODE);
        BigDecimal rsi = BigDecimal.valueOf(100)
            .subtract(
                BigDecimal.valueOf(100).divide(
                    BigDecimal.ONE.add(rs),
                    SCALE,
                    ROUNDING_MODE
                )
            );

        return rsi.setScale(4, ROUNDING_MODE);
    }
}
