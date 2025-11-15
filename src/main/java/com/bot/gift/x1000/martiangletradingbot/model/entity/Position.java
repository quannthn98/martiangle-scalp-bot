package com.bot.gift.x1000.martiangletradingbot.model.entity;

import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeDirection;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Active position entity - represents current open positions
 * This is separate from Trade entity for faster real-time queries
 */
@Entity
@Table(name = "positions", indexes = {
    @Index(name = "idx_symbol_status", columnList = "symbol,status"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_id", nullable = false, unique = true, length = 50)
    private String tradeId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeStatus status;

    @Column(name = "entry_time", nullable = false)
    private Instant entryTime;

    @Column(name = "average_entry_price", precision = 20, scale = 8)
    private BigDecimal averageEntryPrice;

    @Column(name = "current_price", precision = 20, scale = 8)
    private BigDecimal currentPrice;

    @Column(name = "total_position_size", precision = 20, scale = 8)
    private BigDecimal totalPositionSize;

    @Column(name = "remaining_position_size", precision = 20, scale = 8)
    private BigDecimal remainingPositionSize;

    @Column(name = "current_stop_loss", precision = 20, scale = 8)
    private BigDecimal currentStopLoss;

    @Column(name = "tp1_level", precision = 20, scale = 8)
    private BigDecimal tp1Level;

    @Column(name = "tp2_level", precision = 20, scale = 8)
    private BigDecimal tp2Level;

    @Column(name = "tp3_level", precision = 20, scale = 8)
    private BigDecimal tp3Level;

    @Column(name = "tp1_hit")
    private Boolean tp1Hit;

    @Column(name = "tp2_hit")
    private Boolean tp2Hit;

    @Column(name = "highest_price_reached", precision = 20, scale = 8)
    private BigDecimal highestPriceReached;

    @Column(name = "lowest_price_reached", precision = 20, scale = 8)
    private BigDecimal lowestPriceReached;

    @Column(name = "unrealized_pnl", precision = 20, scale = 8)
    private BigDecimal unrealizedPnl;

    @Column(name = "unrealized_pnl_percent", precision = 10, scale = 4)
    private BigDecimal unrealizedPnlPercent;

    @Column(name = "dca_level")
    private Integer dcaLevel;

    @Column(name = "max_dca_level")
    private Integer maxDcaLevel;

    @Column(name = "next_dca_price", precision = 20, scale = 8)
    private BigDecimal nextDcaPrice;

    @Column(name = "last_update_time")
    private Instant lastUpdateTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        lastUpdateTime = Instant.now();
        tp1Hit = false;
        tp2Hit = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        lastUpdateTime = Instant.now();
    }

    /**
     * Update current price and recalculate PnL
     */
    public void updateCurrentPrice(BigDecimal newPrice) {
        this.currentPrice = newPrice;

        // Update highest/lowest prices
        if (highestPriceReached == null || newPrice.compareTo(highestPriceReached) > 0) {
            highestPriceReached = newPrice;
        }
        if (lowestPriceReached == null || newPrice.compareTo(lowestPriceReached) < 0) {
            lowestPriceReached = newPrice;
        }

        // Calculate unrealized PnL
        calculateUnrealizedPnl();
    }

    /**
     * Calculate unrealized profit/loss
     */
    public void calculateUnrealizedPnl() {
        if (currentPrice != null && averageEntryPrice != null && remainingPositionSize != null) {
            BigDecimal priceDiff;
            if (direction == TradeDirection.LONG) {
                priceDiff = currentPrice.subtract(averageEntryPrice);
            } else {
                priceDiff = averageEntryPrice.subtract(currentPrice);
            }

            this.unrealizedPnl = priceDiff.multiply(remainingPositionSize);

            // Calculate percentage
            if (averageEntryPrice.compareTo(BigDecimal.ZERO) > 0) {
                this.unrealizedPnlPercent = priceDiff.divide(averageEntryPrice, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            }
        }
    }

    /**
     * Check if position should be closed based on time
     */
    public boolean shouldCloseByTime(int maxHoldMinutes) {
        if (entryTime == null) return false;
        long minutesHeld = java.time.Duration.between(entryTime, Instant.now()).toMinutes();
        return minutesHeld >= maxHoldMinutes;
    }
}
