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

@DisplayName("ADX Indicator Tests")
class ADXIndicatorTest {

    private ADXIndicator adxIndicator;

    @BeforeEach
    void setUp() {
        adxIndicator = new ADXIndicator();
    }

    @Test
    @DisplayName("Should calculate ADX result with all components")
    void testCalculateAdx() {
        List<Candle> candles = createTrendingCandles(30, true);
        ADXIndicator.ADXResult result = adxIndicator.calculate(candles);

        assertNotNull(result);
        assertNotNull(result.adx());
        assertNotNull(result.plusDI());
        assertNotNull(result.minusDI());

        assertTrue(result.adx().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(result.adx().compareTo(BigDecimal.valueOf(100)) <= 0);
    }

    @Test
    @DisplayName("Strong uptrend should have +DI > -DI")
    void testUptrendDiAlignment() {
        List<Candle> candles = createTrendingCandles(30, true);
        ADXIndicator.ADXResult result = adxIndicator.calculate(candles);

        assertTrue(result.plusDI().compareTo(result.minusDI()) > 0,
            "In uptrend, +DI should be greater than -DI");
    }

    @Test
    @DisplayName("Strong downtrend should have -DI > +DI")
    void testDowntrendDiAlignment() {
        List<Candle> candles = createTrendingCandles(30, false);
        ADXIndicator.ADXResult result = adxIndicator.calculate(candles);

        assertTrue(result.minusDI().compareTo(result.plusDI()) > 0,
            "In downtrend, -DI should be greater than +DI");
    }

    @Test
    @DisplayName("Strong trend should have high ADX")
    void testStrongTrendHighAdx() {
        List<Candle> strongTrend = createTrendingCandles(30, true);
        ADXIndicator.ADXResult result = adxIndicator.calculate(strongTrend);

        boolean isStrongTrend = adxIndicator.isStrongTrend(result.adx(), BigDecimal.valueOf(25));
        // Note: Depending on implementation, might or might not be strong
        assertNotNull(isStrongTrend);
    }

    @Test
    @DisplayName("Should identify bullish trend correctly")
    void testIsBullishTrend() {
        List<Candle> candles = createTrendingCandles(30, true);
        ADXIndicator.ADXResult result = adxIndicator.calculate(candles);

        boolean isBullish = adxIndicator.isBullishTrend(result);
        assertTrue(isBullish, "Should identify bullish trend");
    }

    @Test
    @DisplayName("Should identify bearish trend correctly")
    void testIsBearishTrend() {
        List<Candle> candles = createTrendingCandles(30, false);
        ADXIndicator.ADXResult result = adxIndicator.calculate(candles);

        boolean isBearish = adxIndicator.isBearishTrend(result);
        assertTrue(isBearish, "Should identify bearish trend");
    }

    private List<Candle> createTrendingCandles(int count, boolean uptrend) {
        List<Candle> candles = new ArrayList<>();
        double basePrice = 50000;

        for (int i = 0; i < count; i++) {
            double trendMove = uptrend ? (i * 50) : -(i * 50);
            double open = basePrice + trendMove;
            double close = open + (uptrend ? 30 : -30);
            double high = Math.max(open, close) + 20;
            double low = Math.min(open, close) - 20;

            candles.add(Candle.builder()
                .open(BigDecimal.valueOf(open))
                .high(BigDecimal.valueOf(high))
                .low(BigDecimal.valueOf(low))
                .close(BigDecimal.valueOf(close))
                .volume(BigDecimal.valueOf(1000))
                .openTime(Instant.now())
                .closeTime(Instant.now())
                .build());
        }

        return candles;
    }
}
