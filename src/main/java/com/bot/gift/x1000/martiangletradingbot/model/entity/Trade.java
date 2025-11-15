package com.bot.gift.x1000.martiangletradingbot.model.entity;

import com.bot.gift.x1000.martiangletradingbot.model.enums.ExitReason;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeDirection;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Trade entity - represents a complete trade with all entries and exits
 */
@Entity
@Table(name = "trades", indexes = {
    @Index(name = "idx_symbol_status", columnList = "symbol,status"),
    @Index(name = "idx_entry_time", columnList = "entry_time"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {

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

    // Entry Information
    @Column(name = "entry_time", nullable = false)
    private Instant entryTime;

    @ElementCollection
    @CollectionTable(name = "trade_entries", joinColumns = @JoinColumn(name = "trade_id"))
    @Column(name = "entry_price", precision = 20, scale = 8)
    @Builder.Default
    private List<BigDecimal> entryPrices = new ArrayList<>();

    @Column(name = "average_entry_price", precision = 20, scale = 8)
    private BigDecimal averageEntryPrice;

    @Column(name = "total_position_size", precision = 20, scale = 8)
    private BigDecimal totalPositionSize;

    @Column(name = "remaining_position_size", precision = 20, scale = 8)
    private BigDecimal remainingPositionSize;

    // Exit Information
    @Column(name = "exit_time")
    private Instant exitTime;

    @ElementCollection
    @CollectionTable(name = "trade_exits", joinColumns = @JoinColumn(name = "trade_id"))
    @Column(name = "exit_price", precision = 20, scale = 8)
    @Builder.Default
    private List<BigDecimal> exitPrices = new ArrayList<>();

    @Column(name = "average_exit_price", precision = 20, scale = 8)
    private BigDecimal averageExitPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_reason", length = 30)
    private ExitReason exitReason;

    // Risk Management
    @Column(name = "stop_loss", precision = 20, scale = 8)
    private BigDecimal stopLoss;

    @Column(name = "initial_stop_loss", precision = 20, scale = 8)
    private BigDecimal initialStopLoss;

    @ElementCollection
    @CollectionTable(name = "trade_take_profits", joinColumns = @JoinColumn(name = "trade_id"))
    @Column(name = "take_profit_price", precision = 20, scale = 8)
    @Builder.Default
    private List<BigDecimal> takeProfitLevels = new ArrayList<>();

    @Column(precision = 10, scale = 1)
    private Integer leverage;

    // Performance Metrics
    @Column(name = "profit_loss_usdt", precision = 20, scale = 8)
    private BigDecimal profitLossUsdt;

    @Column(name = "profit_loss_percent", precision = 10, scale = 4)
    private BigDecimal profitLossPercent;

    @Column(name = "max_profit_reached", precision = 20, scale = 8)
    private BigDecimal maxProfitReached;

    @Column(name = "max_drawdown", precision = 20, scale = 8)
    private BigDecimal maxDrawdown;

    @Column(name = "holding_time_minutes")
    private Long holdingTimeMinutes;

    @Column(name = "fees_paid", precision = 20, scale = 8)
    private BigDecimal feesPaid;

    // Technical Indicators at Entry
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "h4Ema34", column = @Column(name = "h4_ema34", precision = 20, scale = 8)),
        @AttributeOverride(name = "h4Ema89", column = @Column(name = "h4_ema89", precision = 20, scale = 8)),
        @AttributeOverride(name = "h4Adx", column = @Column(name = "h4_adx", precision = 10, scale = 4)),
        @AttributeOverride(name = "h1Rsi", column = @Column(name = "h1_rsi", precision = 10, scale = 4)),
        @AttributeOverride(name = "m1Rsi", column = @Column(name = "m1_rsi", precision = 10, scale = 4)),
        @AttributeOverride(name = "m1Atr", column = @Column(name = "m1_atr", precision = 20, scale = 8)),
        @AttributeOverride(name = "m1Ema21", column = @Column(name = "m1_ema21", precision = 20, scale = 8))
    })
    private IndicatorSnapshot indicatorsAtEntry;

    // Market Conditions
    @Column(name = "btc_1h_change_percent", precision = 10, scale = 4)
    private BigDecimal btc1hChangePercent;

    @Column(name = "volume_ratio", precision = 10, scale = 4)
    private BigDecimal volumeRatio;

    @Column(precision = 10, scale = 6)
    private BigDecimal spread;

    // Notes and metadata
    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Calculate holding time in minutes
     */
    public void calculateHoldingTime() {
        if (entryTime != null && exitTime != null) {
            Duration duration = Duration.between(entryTime, exitTime);
            this.holdingTimeMinutes = duration.toMinutes();
        }
    }

    /**
     * Calculate profit/loss percentage
     */
    public void calculateProfitLossPercent() {
        if (averageEntryPrice != null && profitLossUsdt != null && totalPositionSize != null) {
            BigDecimal totalValue = averageEntryPrice.multiply(totalPositionSize);
            if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
                this.profitLossPercent = profitLossUsdt.divide(totalValue, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            }
        }
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndicatorSnapshot {
        private BigDecimal h4Ema34;
        private BigDecimal h4Ema89;
        private BigDecimal h4Adx;
        private BigDecimal h1Rsi;
        private BigDecimal m1Rsi;
        private BigDecimal m1Atr;
        private BigDecimal m1Ema21;
    }
}
