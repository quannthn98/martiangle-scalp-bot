package com.bot.gift.x1000.martiangletradingbot.indicator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Exponential Moving Average (EMA) Calculator
 * Formula: EMA(t) = Price(t) × α + EMA(t-1) × (1 - α)
 * where α = 2 / (Period + 1)
 */
@Component
@Slf4j
public class EMAIndicator {

    private static final int SCALE = 8;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Calculate EMA for a list of prices
     *
     * @param prices List of prices (oldest first)
     * @param period EMA period
     * @return List of EMA values (same length as prices)
     */
    public List<BigDecimal> calculate(List<BigDecimal> prices, int period) {
        if (prices == null || prices.isEmpty()) {
            return new ArrayList<>();
        }

        if (period <= 0) {
            throw new IllegalArgumentException("Period must be greater than 0");
        }

        if (prices.size() < period) {
            log.warn("Not enough data points ({}) for EMA period {}", prices.size(), period);
            return new ArrayList<>();
        }

        List<BigDecimal> emaValues = new ArrayList<>();
        BigDecimal multiplier = calculateMultiplier(period);

        // Calculate initial SMA for the first EMA value
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(prices.get(i));
        }
        BigDecimal initialEma = sum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);

        // Add nulls for the warmup period
        for (int i = 0; i < period - 1; i++) {
            emaValues.add(null);
        }
        emaValues.add(initialEma);

        // Calculate EMA for remaining values
        BigDecimal previousEma = initialEma;
        for (int i = period; i < prices.size(); i++) {
            BigDecimal currentPrice = prices.get(i);
            BigDecimal ema = calculateEmaValue(currentPrice, previousEma, multiplier);
            emaValues.add(ema);
            previousEma = ema;
        }

        return emaValues;
    }

    /**
     * Calculate single EMA value given current price and previous EMA
     *
     * @param currentPrice Current price
     * @param previousEma Previous EMA value
     * @param period EMA period
     * @return Current EMA value
     */
    public BigDecimal calculateNext(BigDecimal currentPrice, BigDecimal previousEma, int period) {
        BigDecimal multiplier = calculateMultiplier(period);
        return calculateEmaValue(currentPrice, previousEma, multiplier);
    }

    /**
     * Get the latest EMA value
     *
     * @param prices List of prices
     * @param period EMA period
     * @return Latest EMA value or null if insufficient data
     */
    public BigDecimal getLatest(List<BigDecimal> prices, int period) {
        List<BigDecimal> emaValues = calculate(prices, period);
        if (emaValues.isEmpty()) {
            return null;
        }
        return emaValues.get(emaValues.size() - 1);
    }

    /**
     * Calculate EMA multiplier (smoothing factor)
     * α = 2 / (Period + 1)
     */
    private BigDecimal calculateMultiplier(int period) {
        return BigDecimal.valueOf(2)
            .divide(BigDecimal.valueOf(period + 1), SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate single EMA value using the formula:
     * EMA = Price × α + EMA(previous) × (1 - α)
     */
    private BigDecimal calculateEmaValue(BigDecimal currentPrice, BigDecimal previousEma, BigDecimal multiplier) {
        BigDecimal priceComponent = currentPrice.multiply(multiplier);
        BigDecimal emaComponent = previousEma.multiply(BigDecimal.ONE.subtract(multiplier));
        return priceComponent.add(emaComponent).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * Check if two EMAs are properly separated (for trend confirmation)
     *
     * @param emaFast Fast EMA value
     * @param emaSlow Slow EMA value
     * @param minSeparationPercent Minimum separation percentage
     * @return true if separation meets minimum threshold
     */
    public boolean checkSeparation(BigDecimal emaFast, BigDecimal emaSlow, BigDecimal minSeparationPercent) {
        if (emaFast == null || emaSlow == null || emaSlow.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        BigDecimal separation = emaFast.subtract(emaSlow).abs()
            .divide(emaSlow, SCALE, ROUNDING_MODE)
            .multiply(BigDecimal.valueOf(100));

        return separation.compareTo(minSeparationPercent) >= 0;
    }

    /**
     * Check if price is within acceptable distance from EMA
     *
     * @param price Current price
     * @param ema EMA value
     * @param maxDistancePercent Maximum distance percentage
     * @return true if price is within threshold
     */
    public boolean isPriceNearEma(BigDecimal price, BigDecimal ema, BigDecimal maxDistancePercent) {
        if (price == null || ema == null || ema.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        BigDecimal distance = price.subtract(ema).abs()
            .divide(ema, SCALE, ROUNDING_MODE)
            .multiply(BigDecimal.valueOf(100));

        return distance.compareTo(maxDistancePercent) <= 0;
    }
}
