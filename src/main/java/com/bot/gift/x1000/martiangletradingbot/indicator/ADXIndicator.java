package com.bot.gift.x1000.martiangletradingbot.indicator;

import com.bot.gift.x1000.martiangletradingbot.model.entity.Candle;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Average Directional Index (ADX) Calculator
 * Measures trend strength (not direction)
 *
 * Formula:
 * +DM = Current High - Previous High (if positive, else 0)
 * -DM = Previous Low - Current Low (if positive, else 0)
 * +DI = (Smoothed +DM / ATR) × 100
 * -DI = (Smoothed -DM / ATR) × 100
 * DX = (|+DI - -DI| / |+DI + -DI|) × 100
 * ADX = Smoothed DX
 */
@Component
@Slf4j
public class ADXIndicator {

    private static final int SCALE = 8;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final ATRIndicator atrIndicator;

    public ADXIndicator(ATRIndicator atrIndicator) {
        this.atrIndicator = atrIndicator;
    }

    /**
     * Calculate ADX for a list of candles
     *
     * @param candles List of candles (oldest first)
     * @param period ADX period (typically 14)
     * @return ADX result with ADX, +DI, and -DI values
     */
    public ADXResult calculate(List<Candle> candles, int period) {
        if (candles == null || candles.size() < period * 2) {
            log.warn("Insufficient candles for ADX calculation");
            return new ADXResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        List<BigDecimal> plusDM = new ArrayList<>();
        List<BigDecimal> minusDM = new ArrayList<>();
        List<BigDecimal> trueRanges = new ArrayList<>();

        // Calculate +DM, -DM, and TR for each candle
        for (int i = 1; i < candles.size(); i++) {
            Candle current = candles.get(i);
            Candle previous = candles.get(i - 1);

            BigDecimal highDiff = current.getHigh().subtract(previous.getHigh());
            BigDecimal lowDiff = previous.getLow().subtract(current.getLow());

            // +DM
            if (highDiff.compareTo(lowDiff) > 0 && highDiff.compareTo(BigDecimal.ZERO) > 0) {
                plusDM.add(highDiff);
            } else {
                plusDM.add(BigDecimal.ZERO);
            }

            // -DM
            if (lowDiff.compareTo(highDiff) > 0 && lowDiff.compareTo(BigDecimal.ZERO) > 0) {
                minusDM.add(lowDiff);
            } else {
                minusDM.add(BigDecimal.ZERO);
            }

            // TR
            trueRanges.add(atrIndicator.calculateTrueRange(current, previous));
        }

        // Smooth +DM, -DM, and ATR
        List<BigDecimal> smoothedPlusDM = smoothValues(plusDM, period);
        List<BigDecimal> smoothedMinusDM = smoothValues(minusDM, period);
        List<BigDecimal> smoothedTR = smoothValues(trueRanges, period);

        // Calculate +DI and -DI
        List<BigDecimal> plusDI = new ArrayList<>();
        List<BigDecimal> minusDI = new ArrayList<>();

        for (int i = 0; i < smoothedTR.size(); i++) {
            if (smoothedTR.get(i) != null && smoothedTR.get(i).compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal pdi = smoothedPlusDM.get(i)
                    .divide(smoothedTR.get(i), SCALE, ROUNDING_MODE)
                    .multiply(BigDecimal.valueOf(100));
                BigDecimal mdi = smoothedMinusDM.get(i)
                    .divide(smoothedTR.get(i), SCALE, ROUNDING_MODE)
                    .multiply(BigDecimal.valueOf(100));

                plusDI.add(pdi);
                minusDI.add(mdi);
            } else {
                plusDI.add(null);
                minusDI.add(null);
            }
        }

        // Calculate DX
        List<BigDecimal> dx = new ArrayList<>();
        for (int i = 0; i < plusDI.size(); i++) {
            if (plusDI.get(i) != null && minusDI.get(i) != null) {
                BigDecimal diSum = plusDI.get(i).add(minusDI.get(i));
                if (diSum.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal diDiff = plusDI.get(i).subtract(minusDI.get(i)).abs();
                    BigDecimal dxValue = diDiff.divide(diSum, SCALE, ROUNDING_MODE)
                        .multiply(BigDecimal.valueOf(100));
                    dx.add(dxValue);
                } else {
                    dx.add(null);
                }
            } else {
                dx.add(null);
            }
        }

        // Smooth DX to get ADX
        List<BigDecimal> adx = smoothValues(dx, period);

        return new ADXResult(adx, plusDI, minusDI);
    }

    /**
     * Get latest ADX value
     */
    public BigDecimal getLatestADX(List<Candle> candles, int period) {
        ADXResult result = calculate(candles, period);
        if (result.adx.isEmpty()) {
            return null;
        }
        return result.adx.get(result.adx.size() - 1);
    }

    /**
     * Check if ADX indicates a strong trend
     */
    public boolean isStrongTrend(BigDecimal adx, BigDecimal threshold) {
        return adx != null && adx.compareTo(threshold) > 0;
    }

    /**
     * Check if trend is bullish (+DI > -DI)
     */
    public boolean isBullishTrend(BigDecimal plusDI, BigDecimal minusDI) {
        return plusDI != null && minusDI != null && plusDI.compareTo(minusDI) > 0;
    }

    /**
     * Check if trend is bearish (-DI > +DI)
     */
    public boolean isBearishTrend(BigDecimal plusDI, BigDecimal minusDI) {
        return plusDI != null && minusDI != null && minusDI.compareTo(plusDI) > 0;
    }

    /**
     * Smooth values using Wilder's smoothing method
     */
    private List<BigDecimal> smoothValues(List<BigDecimal> values, int period) {
        if (values.size() < period) {
            return new ArrayList<>();
        }

        List<BigDecimal> smoothed = new ArrayList<>();

        // Calculate first smoothed value (simple average)
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(values.get(i));
        }
        BigDecimal firstSmoothed = sum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);

        // Add nulls for warmup period
        for (int i = 0; i < period - 1; i++) {
            smoothed.add(null);
        }
        smoothed.add(firstSmoothed);

        // Calculate subsequent smoothed values
        BigDecimal previousSmoothed = firstSmoothed;
        for (int i = period; i < values.size(); i++) {
            BigDecimal current = previousSmoothed.multiply(BigDecimal.valueOf(period - 1))
                .add(values.get(i))
                .divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);
            smoothed.add(current);
            previousSmoothed = current;
        }

        return smoothed;
    }

    @Data
    @AllArgsConstructor
    public static class ADXResult {
        private List<BigDecimal> adx;
        private List<BigDecimal> plusDI;
        private List<BigDecimal> minusDI;
    }
}
