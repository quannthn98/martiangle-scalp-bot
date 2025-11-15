package com.bot.gift.x1000.martiangletradingbot.model.entity;

import com.bot.gift.x1000.martiangletradingbot.model.enums.Timeframe;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Candle/Kline data entity
 */
@Entity
@Table(name = "candles", indexes = {
    @Index(name = "idx_symbol_timeframe_opentime", columnList = "symbol,timeframe,open_time", unique = true),
    @Index(name = "idx_symbol_timeframe", columnList = "symbol,timeframe")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Timeframe timeframe;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    @Column(name = "close_time", nullable = false)
    private Instant closeTime;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal open;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal high;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal low;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal close;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal volume;

    @Column(name = "quote_volume", precision = 20, scale = 2)
    private BigDecimal quoteVolume;

    @Column(name = "number_of_trades")
    private Integer numberOfTrades;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    /**
     * Check if candle is green (bullish)
     */
    public boolean isGreen() {
        return close.compareTo(open) > 0;
    }

    /**
     * Check if candle is red (bearish)
     */
    public boolean isRed() {
        return close.compareTo(open) < 0;
    }

    /**
     * Get candle body size
     */
    public BigDecimal getBodySize() {
        return close.subtract(open).abs();
    }

    /**
     * Get candle range (high - low)
     */
    public BigDecimal getRange() {
        return high.subtract(low);
    }
}
