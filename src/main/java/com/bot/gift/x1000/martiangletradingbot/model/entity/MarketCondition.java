package com.bot.gift.x1000.martiangletradingbot.model.entity;

import com.bot.gift.x1000.martiangletradingbot.model.enums.Timeframe;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TrendDirection;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Market condition snapshot for a symbol
 * Used for trend detection and market scanning
 */
@Entity
@Table(name = "market_conditions", indexes = {
    @Index(name = "idx_symbol_timeframe", columnList = "symbol,timeframe"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Timeframe timeframe;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "current_price", precision = 20, scale = 8)
    private BigDecimal currentPrice;

    // EMA Values
    @Column(name = "ema21", precision = 20, scale = 8)
    private BigDecimal ema21;

    @Column(name = "ema34", precision = 20, scale = 8)
    private BigDecimal ema34;

    @Column(name = "ema89", precision = 20, scale = 8)
    private BigDecimal ema89;

    // Technical Indicators
    @Column(precision = 10, scale = 4)
    private BigDecimal rsi;

    @Column(precision = 20, scale = 8)
    private BigDecimal atr;

    @Column(precision = 10, scale = 4)
    private BigDecimal adx;

    @Column(name = "plus_di", precision = 10, scale = 4)
    private BigDecimal plusDI;

    @Column(name = "minus_di", precision = 10, scale = 4)
    private BigDecimal minusDI;

    // Bollinger Bands
    @Column(name = "bb_upper", precision = 20, scale = 8)
    private BigDecimal bbUpper;

    @Column(name = "bb_middle", precision = 20, scale = 8)
    private BigDecimal bbMiddle;

    @Column(name = "bb_lower", precision = 20, scale = 8)
    private BigDecimal bbLower;

    @Column(name = "bb_bandwidth", precision = 10, scale = 6)
    private BigDecimal bbBandwidth;

    // Volume
    @Column(name = "current_volume", precision = 20, scale = 2)
    private BigDecimal currentVolume;

    @Column(name = "volume_ma", precision = 20, scale = 2)
    private BigDecimal volumeMa;

    @Column(name = "volume_ratio", precision = 10, scale = 4)
    private BigDecimal volumeRatio;

    // Trend Analysis
    @Enumerated(EnumType.STRING)
    @Column(name = "trend_direction", length = 10)
    private TrendDirection trendDirection;

    @Column(name = "ema_separation_percent", precision = 10, scale = 4)
    private BigDecimal emaSeparationPercent;

    @Column(name = "price_distance_from_ema34_percent", precision = 10, scale = 4)
    private BigDecimal priceDistanceFromEma34Percent;

    @Column(name = "is_trending")
    private Boolean isTrending;

    @Column(name = "is_tradeable")
    private Boolean isTradeable;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    /**
     * Check if EMA alignment is bullish
     */
    public boolean isBullishEmaAlignment() {
        return currentPrice != null && ema34 != null && ema89 != null &&
            currentPrice.compareTo(ema34) > 0 && ema34.compareTo(ema89) > 0;
    }

    /**
     * Check if EMA alignment is bearish
     */
    public boolean isBearishEmaAlignment() {
        return currentPrice != null && ema34 != null && ema89 != null &&
            currentPrice.compareTo(ema34) < 0 && ema34.compareTo(ema89) < 0;
    }

    /**
     * Check if in BB squeeze (low volatility)
     */
    public boolean isInBbSqueeze(BigDecimal squeezeThreshold) {
        return bbBandwidth != null && bbBandwidth.compareTo(squeezeThreshold) < 0;
    }
}
