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

@DisplayName("Volume Indicator Tests")
class VolumeIndicatorTest {

    private VolumeIndicator volumeIndicator;

    @BeforeEach
    void setUp() {
        volumeIndicator = new VolumeIndicator();
    }

    @Test
    @DisplayName("Should calculate volume moving average")
    void testCalculateVolumeMA() {
        List<Candle> candles = createCandlesWithVolume(25, 1000);
        BigDecimal volumeMA = volumeIndicator.calculateVolumeMA(candles, 20);

        assertNotNull(volumeMA);
        assertTrue(volumeMA.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(volumeMA.compareTo(BigDecimal.valueOf(1500)) < 0);
    }

    @Test
    @DisplayName("Should detect volume spike")
    void testIsVolumeSpike() {
        List<Candle> normalCandles = createCandlesWithVolume(20, 1000);
        // Add a spike candle with 4x volume
        normalCandles.add(createCandleWithVolume(50000, 4000));

        boolean isSpike = volumeIndicator.isVolumeSpike(normalCandles, BigDecimal.valueOf(3.0));

        assertTrue(isSpike, "Should detect volume spike");
    }

    @Test
    @DisplayName("Should NOT detect spike with normal volume")
    void testNoVolumeSpike() {
        List<Candle> candles = createCandlesWithVolume(25, 1000);

        boolean isSpike = volumeIndicator.isVolumeSpike(candles, BigDecimal.valueOf(3.0));

        assertFalse(isSpike, "Should not detect spike with normal volume");
    }

    @Test
    @DisplayName("Should confirm trend with increasing volume")
    void testVolumeConfirmsTrend() {
        List<Candle> candles = new ArrayList<>();

        // Create uptrend with increasing volume
        for (int i = 0; i < 10; i++) {
            double price = 50000 + (i * 100);
            double volume = 1000 + (i * 100); // Increasing volume
            candles.add(createCandleWithVolume(price, volume));
        }

        boolean confirms = volumeIndicator.volumeConfirmsTrend(candles, true);

        assertTrue(confirms, "Increasing volume should confirm uptrend");
    }

    @Test
    @DisplayName("Should handle insufficient data")
    void testInsufficientData() {
        List<Candle> candles = createCandlesWithVolume(5, 1000);
        BigDecimal volumeMA = volumeIndicator.calculateVolumeMA(candles, 20);

        // Should handle gracefully
        assertTrue(volumeMA == null || volumeMA.compareTo(BigDecimal.ZERO) >= 0);
    }

    private List<Candle> createCandlesWithVolume(int count, double baseVolume) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double volume = baseVolume + (Math.random() - 0.5) * 200;
            candles.add(createCandleWithVolume(50000, volume));
        }
        return candles;
    }

    private Candle createCandleWithVolume(double price, double volume) {
        return Candle.builder()
            .open(BigDecimal.valueOf(price))
            .high(BigDecimal.valueOf(price * 1.01))
            .low(BigDecimal.valueOf(price * 0.99))
            .close(BigDecimal.valueOf(price * 1.005))
            .volume(BigDecimal.valueOf(volume))
            .openTime(Instant.now())
            .closeTime(Instant.now())
            .build();
    }
}
