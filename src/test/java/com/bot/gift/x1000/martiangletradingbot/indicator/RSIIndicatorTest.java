package com.bot.gift.x1000.martiangletradingbot.indicator;

import com.bot.gift.x1000.martiangletradingbot.model.entity.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RSIIndicator
 * Tests RSI calculation and divergence detection
 */
@DisplayName("RSI Indicator Tests")
class RSIIndicatorTest {

    private RSIIndicator rsiIndicator;

    @BeforeEach
    void setUp() {
        rsiIndicator = new RSIIndicator();
    }

    @Test
    @DisplayName("Should calculate RSI correctly for uptrend")
    void testCalculateRsiUptrend() {
        // Given: Price series in uptrend
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            prices.add(BigDecimal.valueOf(50000 + (i * 100))); // Steady uptrend
        }

        // When: Calculate RSI(14)
        List<BigDecimal> rsiValues = rsiIndicator.calculate(prices, 14);

        // Then: RSI should be above 50 in uptrend
        assertEquals(prices.size(), rsiValues.size());

        // First 13 values should be null
        for (int i = 0; i < 13; i++) {
            assertNull(rsiValues.get(i));
        }

        // RSI values from index 13 onwards should be > 50 for uptrend
        for (int i = 13; i < rsiValues.size(); i++) {
            assertNotNull(rsiValues.get(i), "RSI at index " + i + " should not be null");
            assertTrue(rsiValues.get(i).compareTo(BigDecimal.valueOf(50)) > 0,
                "RSI should be above 50 in uptrend");
            assertTrue(rsiValues.get(i).compareTo(BigDecimal.valueOf(100)) <= 0,
                "RSI should not exceed 100");
        }
    }

    @Test
    @DisplayName("Should calculate RSI correctly for downtrend")
    void testCalculateRsiDowntrend() {
        // Given: Price series in downtrend
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            prices.add(BigDecimal.valueOf(50000 - (i * 100))); // Steady downtrend
        }

        // When: Calculate RSI(14)
        List<BigDecimal> rsiValues = rsiIndicator.calculate(prices, 14);

        // Then: RSI should be below 50 in downtrend
        for (int i = 13; i < rsiValues.size(); i++) {
            assertNotNull(rsiValues.get(i));
            assertTrue(rsiValues.get(i).compareTo(BigDecimal.valueOf(50)) < 0,
                "RSI should be below 50 in downtrend at index " + i);
            assertTrue(rsiValues.get(i).compareTo(BigDecimal.ZERO) >= 0,
                "RSI should not be negative");
        }
    }

    @Test
    @DisplayName("RSI should be between 0 and 100")
    void testRsiBounds() {
        // Given: Highly volatile price series
        List<BigDecimal> prices = new ArrayList<>();
        double basePrice = 50000;
        for (int i = 0; i < 50; i++) {
            double variation = (Math.random() - 0.5) * 0.2; // +/- 10% variation
            prices.add(BigDecimal.valueOf(basePrice * (1 + variation)));
        }

        // When: Calculate RSI
        List<BigDecimal> rsiValues = rsiIndicator.calculate(prices, 14);

        // Then: All RSI values should be between 0 and 100
        for (int i = 13; i < rsiValues.size(); i++) {
            assertNotNull(rsiValues.get(i));
            assertTrue(rsiValues.get(i).compareTo(BigDecimal.ZERO) >= 0,
                "RSI should be >= 0");
            assertTrue(rsiValues.get(i).compareTo(BigDecimal.valueOf(100)) <= 0,
                "RSI should be <= 100");
        }
    }

    @Test
    @DisplayName("Should detect bullish divergence")
    void testDetectBullishDivergence() {
        // Given: Candles with lower lows in price but higher lows in RSI
        List<Candle> candles = new ArrayList<>();
        List<BigDecimal> rsiValues = new ArrayList<>();

        // Create scenario: Price making lower lows, but RSI making higher lows
        // Time 1: Price at 50000, RSI at 30
        candles.add(createCandle(50000, 50500, 49500, 50000));
        rsiValues.add(BigDecimal.valueOf(30));

        // Middle candles
        for (int i = 0; i < 8; i++) {
            candles.add(createCandle(49900, 50100, 49800, 49900));
            rsiValues.add(BigDecimal.valueOf(28 + i));
        }

        // Time 2: Price at 49500 (lower low), RSI at 35 (higher low)
        candles.add(createCandle(49500, 49800, 49300, 49600));
        rsiValues.add(BigDecimal.valueOf(35));

        // When: Check for bullish divergence
        boolean hasDivergence = rsiIndicator.detectBullishDivergence(candles, rsiValues, 10);

        // Then: Should detect bullish divergence
        assertTrue(hasDivergence, "Should detect bullish divergence");
    }

    @Test
    @DisplayName("Should detect bearish divergence")
    void testDetectBearishDivergence() {
        // Given: Candles with higher highs in price but lower highs in RSI
        List<Candle> candles = new ArrayList<>();
        List<BigDecimal> rsiValues = new ArrayList<>();

        // Create scenario: Price making higher highs, but RSI making lower highs
        // Time 1: Price at 50000, RSI at 70
        candles.add(createCandle(50000, 50500, 49800, 50400));
        rsiValues.add(BigDecimal.valueOf(70));

        // Middle candles
        for (int i = 0; i < 8; i++) {
            candles.add(createCandle(50200, 50400, 50100, 50300));
            rsiValues.add(BigDecimal.valueOf(68 - i));
        }

        // Time 2: Price at 50600 (higher high), RSI at 65 (lower high)
        candles.add(createCandle(50400, 50600, 50300, 50500));
        rsiValues.add(BigDecimal.valueOf(65));

        // When: Check for bearish divergence
        boolean hasDivergence = rsiIndicator.detectBearishDivergence(candles, rsiValues, 10);

        // Then: Should detect bearish divergence
        assertTrue(hasDivergence, "Should detect bearish divergence");
    }

    @Test
    @DisplayName("Should NOT detect divergence when none exists")
    void testNoDivergence() {
        // Given: Candles with normal price/RSI relationship
        List<Candle> candles = new ArrayList<>();
        List<BigDecimal> rsiValues = new ArrayList<>();

        for (int i = 0; i < 15; i++) {
            double price = 50000 + (i * 50);
            candles.add(createCandle(price, price + 100, price - 100, price + 50));
            rsiValues.add(BigDecimal.valueOf(50 + i)); // RSI increasing with price
        }

        // When: Check for divergences
        boolean bullishDiv = rsiIndicator.detectBullishDivergence(candles, rsiValues, 10);
        boolean bearishDiv = rsiIndicator.detectBearishDivergence(candles, rsiValues, 10);

        // Then: Should not detect any divergence
        assertFalse(bullishDiv, "Should not detect bullish divergence");
        assertFalse(bearishDiv, "Should not detect bearish divergence");
    }

    @Test
    @DisplayName("Should handle insufficient data for divergence detection")
    void testInsufficientDataForDivergence() {
        // Given: Not enough candles
        List<Candle> candles = new ArrayList<>();
        List<BigDecimal> rsiValues = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            candles.add(createCandle(50000, 50100, 49900, 50000));
            rsiValues.add(BigDecimal.valueOf(50));
        }

        // When: Try to detect divergence with lookback of 10
        boolean bullishDiv = rsiIndicator.detectBullishDivergence(candles, rsiValues, 10);
        boolean bearishDiv = rsiIndicator.detectBearishDivergence(candles, rsiValues, 10);

        // Then: Should return false (not enough data)
        assertFalse(bullishDiv);
        assertFalse(bearishDiv);
    }

    @Test
    @DisplayName("Should handle empty price list")
    void testEmptyPriceList() {
        // Given: Empty price list
        List<BigDecimal> prices = new ArrayList<>();

        // When: Calculate RSI
        List<BigDecimal> rsiValues = rsiIndicator.calculate(prices, 14);

        // Then: Should return empty list
        assertTrue(rsiValues.isEmpty());
    }

    @Test
    @DisplayName("Should handle flat price series (no change)")
    void testFlatPriceSeries() {
        // Given: Flat price series (all same price)
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            prices.add(BigDecimal.valueOf(50000));
        }

        // When: Calculate RSI
        List<BigDecimal> rsiValues = rsiIndicator.calculate(prices, 14);

        // Then: RSI should be around 50 (neutral) for flat prices
        // Or could be null/0 depending on implementation
        assertNotNull(rsiValues);
        assertEquals(20, rsiValues.size());
    }

    @Test
    @DisplayName("RSI should react quickly to price changes")
    void testRsiReactivity() {
        // Given: Stable prices followed by sharp increase
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            prices.add(BigDecimal.valueOf(50000));
        }

        // Add sharp price increases
        prices.add(BigDecimal.valueOf(51000));
        prices.add(BigDecimal.valueOf(52000));
        prices.add(BigDecimal.valueOf(53000));

        // When: Calculate RSI
        List<BigDecimal> rsiValues = rsiIndicator.calculate(prices, 14);

        // Then: RSI should increase significantly
        BigDecimal rsiBeforeSpike = rsiValues.get(19);
        BigDecimal rsiAfterSpike = rsiValues.get(22);

        assertNotNull(rsiBeforeSpike);
        assertNotNull(rsiAfterSpike);
        assertTrue(rsiAfterSpike.compareTo(rsiBeforeSpike) > 0,
            "RSI should increase after sharp price rise");
        assertTrue(rsiAfterSpike.compareTo(BigDecimal.valueOf(70)) >= 0,
            "RSI should be overbought after sharp rise");
    }

    /**
     * Helper: Create a test candle
     */
    private Candle createCandle(double open, double high, double low, double close) {
        return Candle.builder()
            .open(BigDecimal.valueOf(open))
            .high(BigDecimal.valueOf(high))
            .low(BigDecimal.valueOf(low))
            .close(BigDecimal.valueOf(close))
            .openTime(Instant.now())
            .closeTime(Instant.now())
            .volume(BigDecimal.valueOf(1000))
            .build();
    }
}
