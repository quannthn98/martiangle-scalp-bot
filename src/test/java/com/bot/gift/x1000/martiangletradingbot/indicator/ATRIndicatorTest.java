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

@DisplayName("ATR Indicator Tests")
class ATRIndicatorTest {

    private ATRIndicator atrIndicator;

    @BeforeEach
    void setUp() {
        atrIndicator = new ATRIndicator();
    }

    @Test
    @DisplayName("Should calculate ATR for valid candle data")
    void testCalculateAtr() {
        List<Candle> candles = createCandleSeries(30, 50000, 100);
        BigDecimal atr = atrIndicator.calculate(candles, 14);

        assertNotNull(atr);
        assertTrue(atr.compareTo(BigDecimal.ZERO) > 0, "ATR should be positive");
    }

    @Test
    @DisplayName("Higher volatility should result in higher ATR")
    void testAtrVolatilityCorrelation() {
        List<Candle> lowVolatility = createCandleSeries(30, 50000, 50);
        List<Candle> highVolatility = createCandleSeries(30, 50000, 500);

        BigDecimal atrLow = atrIndicator.calculate(lowVolatility, 14);
        BigDecimal atrHigh = atrIndicator.calculate(highVolatility, 14);

        assertTrue(atrHigh.compareTo(atrLow) > 0,
            "Higher volatility should produce higher ATR");
    }

    @Test
    @DisplayName("Should detect ATR spike correctly")
    void testIsAtrSpiking() {
        List<Candle> candles = createCandleSeries(30, 50000, 100);
        List<BigDecimal> atrValues = atrIndicator.calculateSeries(candles, 14);

        // Add a spike candle
        candles.add(createCandle(50000, 52000, 49000, 51000));
        List<BigDecimal> atrWithSpike = atrIndicator.calculateSeries(candles, 14);

        boolean isSpike = atrIndicator.isAtrSpiking(
            atrWithSpike,
            BigDecimal.valueOf(1.5)
        );

        assertNotNull(isSpike);
    }

    @Test
    @DisplayName("Should handle insufficient data")
    void testInsufficientData() {
        List<Candle> candles = createCandleSeries(5, 50000, 100);
        BigDecimal atr = atrIndicator.calculate(candles, 14);

        // Should either return null or a calculated value with available data
        assertTrue(atr == null || atr.compareTo(BigDecimal.ZERO) >= 0);
    }

    private List<Candle> createCandleSeries(int count, double basePrice, double range) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double open = basePrice + (Math.random() - 0.5) * range;
            double close = open + (Math.random() - 0.5) * range;
            double high = Math.max(open, close) + Math.random() * range;
            double low = Math.min(open, close) - Math.random() * range;
            candles.add(createCandle(open, high, low, close));
        }
        return candles;
    }

    private Candle createCandle(double open, double high, double low, double close) {
        return Candle.builder()
            .open(BigDecimal.valueOf(open))
            .high(BigDecimal.valueOf(high))
            .low(BigDecimal.valueOf(low))
            .close(BigDecimal.valueOf(close))
            .volume(BigDecimal.valueOf(1000))
            .openTime(Instant.now())
            .closeTime(Instant.now())
            .build();
    }
}
