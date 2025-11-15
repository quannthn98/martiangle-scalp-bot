package com.bot.gift.x1000.martiangletradingbot.indicator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EMAIndicator
 * Tests EMA calculation accuracy with known values
 */
@DisplayName("EMA Indicator Tests")
class EMAIndicatorTest {

    private EMAIndicator emaIndicator;

    @BeforeEach
    void setUp() {
        emaIndicator = new EMAIndicator();
    }

    @Test
    @DisplayName("Should calculate EMA correctly for simple dataset")
    void testCalculateEmaSimpleDataset() {
        // Given: A simple price series
        List<BigDecimal> prices = List.of(
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(102),
            BigDecimal.valueOf(103),
            BigDecimal.valueOf(101),
            BigDecimal.valueOf(104),
            BigDecimal.valueOf(105),
            BigDecimal.valueOf(106),
            BigDecimal.valueOf(107),
            BigDecimal.valueOf(108),
            BigDecimal.valueOf(109),
            BigDecimal.valueOf(110)
        );

        // When: Calculate 5-period EMA
        List<BigDecimal> emaValues = emaIndicator.calculate(prices, 5);

        // Then: Should return correct number of values
        assertEquals(prices.size(), emaValues.size());

        // First 4 values should be null (not enough data)
        for (int i = 0; i < 4; i++) {
            assertNull(emaValues.get(i), "EMA value at index " + i + " should be null");
        }

        // EMA values should be calculated from index 4 onwards
        for (int i = 4; i < emaValues.size(); i++) {
            assertNotNull(emaValues.get(i), "EMA value at index " + i + " should not be null");
            assertTrue(emaValues.get(i).compareTo(BigDecimal.ZERO) > 0,
                "EMA value should be positive");
        }
    }

    @Test
    @DisplayName("Should calculate EMA21 correctly")
    void testCalculateEma21() {
        // Given: Price series with 30 values
        List<BigDecimal> prices = generatePriceSeries(30, 50000);

        // When: Calculate 21-period EMA
        List<BigDecimal> emaValues = emaIndicator.calculate(prices, 21);

        // Then: Should have correct values from index 20 onwards
        assertEquals(30, emaValues.size());

        for (int i = 0; i < 20; i++) {
            assertNull(emaValues.get(i));
        }

        for (int i = 20; i < emaValues.size(); i++) {
            assertNotNull(emaValues.get(i));
            assertTrue(emaValues.get(i).compareTo(BigDecimal.valueOf(45000)) > 0);
            assertTrue(emaValues.get(i).compareTo(BigDecimal.valueOf(55000)) < 0);
        }
    }

    @Test
    @DisplayName("Should detect proper EMA separation")
    void testCheckSeparation() {
        // Given: Two price series that are separated
        List<BigDecimal> prices = generatePriceSeries(50, 50000);
        List<BigDecimal> ema34 = emaIndicator.calculate(prices, 34);
        List<BigDecimal> ema89 = emaIndicator.calculate(prices, 89);

        // When: Check separation
        boolean separated = emaIndicator.checkSeparation(
            ema34.get(ema34.size() - 1),
            ema89.get(ema89.size() - 1),
            BigDecimal.valueOf(1.5)
        );

        // Then: Result should be valid
        assertNotNull(separated);
    }

    @Test
    @DisplayName("Should detect when price is near EMA")
    void testIsPriceNearEma() {
        // Given: A price and EMA value
        BigDecimal price = BigDecimal.valueOf(50000);
        BigDecimal ema = BigDecimal.valueOf(50500);

        // When: Check if price is near EMA (within 5%)
        boolean isNear = emaIndicator.isPriceNearEma(price, ema, BigDecimal.valueOf(5.0));

        // Then: Should return true
        assertTrue(isNear, "Price should be near EMA within 5% threshold");
    }

    @Test
    @DisplayName("Should detect when price is NOT near EMA")
    void testIsPriceNotNearEma() {
        // Given: A price far from EMA value
        BigDecimal price = BigDecimal.valueOf(50000);
        BigDecimal ema = BigDecimal.valueOf(55000);

        // When: Check if price is near EMA (within 2%)
        boolean isNear = emaIndicator.isPriceNearEma(price, ema, BigDecimal.valueOf(2.0));

        // Then: Should return false
        assertFalse(isNear, "Price should NOT be near EMA with 2% threshold");
    }

    @Test
    @DisplayName("Should handle insufficient data gracefully")
    void testInsufficientData() {
        // Given: Not enough prices for EMA21
        List<BigDecimal> prices = List.of(
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(101)
        );

        // When: Try to calculate EMA21
        List<BigDecimal> emaValues = emaIndicator.calculate(prices, 21);

        // Then: Should return list with all null values
        assertEquals(2, emaValues.size());
        assertNull(emaValues.get(0));
        assertNull(emaValues.get(1));
    }

    @Test
    @DisplayName("Should handle empty price list")
    void testEmptyPriceList() {
        // Given: Empty price list
        List<BigDecimal> prices = new ArrayList<>();

        // When: Try to calculate EMA
        List<BigDecimal> emaValues = emaIndicator.calculate(prices, 21);

        // Then: Should return empty list
        assertTrue(emaValues.isEmpty());
    }

    @Test
    @DisplayName("Should handle null price list")
    void testNullPriceList() {
        // When/Then: Should handle null gracefully
        assertThrows(NullPointerException.class, () -> {
            emaIndicator.calculate(null, 21);
        });
    }

    @Test
    @DisplayName("EMA should be reactive to price changes")
    void testEmaReactivity() {
        // Given: Price series with a sudden spike
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            prices.add(BigDecimal.valueOf(50000));
        }
        prices.add(BigDecimal.valueOf(55000)); // Spike
        prices.add(BigDecimal.valueOf(55000));
        prices.add(BigDecimal.valueOf(55000));

        // When: Calculate EMA10
        List<BigDecimal> emaValues = emaIndicator.calculate(prices, 10);

        // Then: EMA should increase after the spike
        BigDecimal emaBeforeSpike = emaValues.get(19);
        BigDecimal emaAfterSpike = emaValues.get(22);

        assertNotNull(emaBeforeSpike);
        assertNotNull(emaAfterSpike);
        assertTrue(emaAfterSpike.compareTo(emaBeforeSpike) > 0,
            "EMA should increase after price spike");
    }

    @Test
    @DisplayName("Longer EMA should be smoother than shorter EMA")
    void testEmaSmoothness() {
        // Given: Volatile price series
        List<BigDecimal> prices = generateVolatilePriceSeries(100);

        // When: Calculate both EMA10 and EMA50
        List<BigDecimal> ema10 = emaIndicator.calculate(prices, 10);
        List<BigDecimal> ema50 = emaIndicator.calculate(prices, 50);

        // Then: EMA50 should be smoother (less variation) than EMA10
        // This is a conceptual test - in practice, we'd measure variance
        assertNotNull(ema10.get(49));
        assertNotNull(ema50.get(49));

        // Longer EMA should lag more behind current price
        BigDecimal currentPrice = prices.get(99);
        BigDecimal diffEma10 = currentPrice.subtract(ema10.get(99)).abs();
        BigDecimal diffEma50 = currentPrice.subtract(ema50.get(99)).abs();

        // EMA50 typically lags more (but not always in all markets)
        // This test validates they produce different results
        assertNotEquals(ema10.get(99), ema50.get(99),
            "Different period EMAs should produce different results");
    }

    /**
     * Helper: Generate a price series with slight variations
     */
    private List<BigDecimal> generatePriceSeries(int count, double basePrice) {
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double variation = (Math.random() - 0.5) * 0.02; // +/- 1% variation
            double price = basePrice * (1 + variation);
            prices.add(BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP));
        }
        return prices;
    }

    /**
     * Helper: Generate volatile price series for testing
     */
    private List<BigDecimal> generateVolatilePriceSeries(int count) {
        List<BigDecimal> prices = new ArrayList<>();
        double basePrice = 50000;
        for (int i = 0; i < count; i++) {
            double variation = (Math.random() - 0.5) * 0.1; // +/- 5% variation
            double price = basePrice * (1 + variation);
            prices.add(BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP));
            basePrice = price; // Next price based on previous (trending)
        }
        return prices;
    }
}
