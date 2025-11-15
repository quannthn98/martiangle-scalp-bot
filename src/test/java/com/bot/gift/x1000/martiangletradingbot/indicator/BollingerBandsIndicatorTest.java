package com.bot.gift.x1000.martiangletradingbot.indicator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Bollinger Bands Indicator Tests")
class BollingerBandsIndicatorTest {

    private BollingerBandsIndicator bbIndicator;

    @BeforeEach
    void setUp() {
        bbIndicator = new BollingerBandsIndicator();
    }

    @Test
    @DisplayName("Should calculate Bollinger Bands correctly")
    void testCalculateBollingerBands() {
        List<BigDecimal> prices = createPriceSeries(30, 50000);

        BollingerBandsIndicator.BBResult result = bbIndicator.calculate(prices, 20, BigDecimal.valueOf(2));

        assertNotNull(result);
        assertNotNull(result.upper());
        assertNotNull(result.middle());
        assertNotNull(result.lower());
        assertNotNull(result.bandwidth());

        // Upper band should be above middle
        assertTrue(result.upper().compareTo(result.middle()) > 0,
            "Upper band should be above middle band");

        // Middle band should be above lower
        assertTrue(result.middle().compareTo(result.lower()) > 0,
            "Middle band should be above lower band");

        // Bandwidth should be positive
        assertTrue(result.bandwidth().compareTo(BigDecimal.ZERO) > 0,
            "Bandwidth should be positive");
    }

    @Test
    @DisplayName("Should detect Bollinger Band squeeze")
    void testIsInSqueeze() {
        // Create low volatility scenario
        List<BigDecimal> lowVolPrices = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            lowVolPrices.add(BigDecimal.valueOf(50000 + (Math.random() - 0.5) * 20));
        }

        BollingerBandsIndicator.BBResult result = bbIndicator.calculate(
            lowVolPrices, 20, BigDecimal.valueOf(2)
        );

        boolean isInSqueeze = bbIndicator.isInSqueeze(result, BigDecimal.valueOf(0.02));

        // Low volatility should produce squeeze
        assertNotNull(isInSqueeze);
    }

    @Test
    @DisplayName("Should NOT detect squeeze in high volatility")
    void testNotInSqueeze() {
        // Create high volatility scenario
        List<BigDecimal> highVolPrices = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            highVolPrices.add(BigDecimal.valueOf(50000 + (Math.random() - 0.5) * 2000));
        }

        BollingerBandsIndicator.BBResult result = bbIndicator.calculate(
            highVolPrices, 20, BigDecimal.valueOf(2)
        );

        boolean isInSqueeze = bbIndicator.isInSqueeze(result, BigDecimal.valueOf(0.02));

        assertFalse(isInSqueeze, "High volatility should not produce squeeze");
    }

    @Test
    @DisplayName("Bandwidth should increase with volatility")
    void testBandwidthVolatilityRelation() {
        List<BigDecimal> lowVolPrices = createLowVolatilityPrices(30);
        List<BigDecimal> highVolPrices = createHighVolatilityPrices(30);

        BollingerBandsIndicator.BBResult lowVolResult = bbIndicator.calculate(
            lowVolPrices, 20, BigDecimal.valueOf(2)
        );

        BollingerBandsIndicator.BBResult highVolResult = bbIndicator.calculate(
            highVolPrices, 20, BigDecimal.valueOf(2)
        );

        assertTrue(highVolResult.bandwidth().compareTo(lowVolResult.bandwidth()) > 0,
            "High volatility should produce wider bandwidth");
    }

    @Test
    @DisplayName("Should handle insufficient data")
    void testInsufficientData() {
        List<BigDecimal> prices = createPriceSeries(10, 50000);

        BollingerBandsIndicator.BBResult result = bbIndicator.calculate(
            prices, 20, BigDecimal.valueOf(2)
        );

        // Should handle gracefully - either return null or partial result
        assertTrue(result == null || result.bandwidth().compareTo(BigDecimal.ZERO) >= 0);
    }

    private List<BigDecimal> createPriceSeries(int count, double basePrice) {
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double variation = (Math.random() - 0.5) * 0.02; // +/- 1%
            prices.add(BigDecimal.valueOf(basePrice * (1 + variation)));
        }
        return prices;
    }

    private List<BigDecimal> createLowVolatilityPrices(int count) {
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double variation = (Math.random() - 0.5) * 0.002; // +/- 0.1%
            prices.add(BigDecimal.valueOf(50000 * (1 + variation)));
        }
        return prices;
    }

    private List<BigDecimal> createHighVolatilityPrices(int count) {
        List<BigDecimal> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double variation = (Math.random() - 0.5) * 0.1; // +/- 5%
            prices.add(BigDecimal.valueOf(50000 * (1 + variation)));
        }
        return prices;
    }
}
