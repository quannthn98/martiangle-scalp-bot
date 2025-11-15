package com.bot.gift.x1000.martiangletradingbot.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily performance tracking entity
 * Used for enforcing daily limits and tracking overall performance
 */
@Entity
@Table(name = "daily_performance", indexes = {
    @Index(name = "idx_trading_date", columnList = "trading_date", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trading_date", nullable = false, unique = true)
    private LocalDate tradingDate;

    @Column(name = "starting_balance", precision = 20, scale = 2)
    private BigDecimal startingBalance;

    @Column(name = "ending_balance", precision = 20, scale = 2)
    private BigDecimal endingBalance;

    @Column(name = "total_trades")
    private Integer totalTrades;

    @Column(name = "winning_trades")
    private Integer winningTrades;

    @Column(name = "losing_trades")
    private Integer losingTrades;

    @Column(name = "total_profit", precision = 20, scale = 8)
    private BigDecimal totalProfit;

    @Column(name = "total_loss", precision = 20, scale = 8)
    private BigDecimal totalLoss;

    @Column(name = "net_pnl", precision = 20, scale = 8)
    private BigDecimal netPnl;

    @Column(name = "net_pnl_percent", precision = 10, scale = 4)
    private BigDecimal netPnlPercent;

    @Column(name = "win_rate", precision = 10, scale = 4)
    private BigDecimal winRate;

    @Column(name = "profit_factor", precision = 10, scale = 4)
    private BigDecimal profitFactor;

    @Column(name = "largest_win", precision = 20, scale = 8)
    private BigDecimal largestWin;

    @Column(name = "largest_loss", precision = 20, scale = 8)
    private BigDecimal largestLoss;

    @Column(name = "average_win", precision = 20, scale = 8)
    private BigDecimal averageWin;

    @Column(name = "average_loss", precision = 20, scale = 8)
    private BigDecimal averageLoss;

    @Column(name = "total_fees", precision = 20, scale = 8)
    private BigDecimal totalFees;

    @Column(name = "max_drawdown", precision = 20, scale = 8)
    private BigDecimal maxDrawdown;

    @Column(name = "daily_limit_reached")
    private Boolean dailyLimitReached;

    @Column(name = "trading_stopped")
    private Boolean tradingStopped;

    @Column(name = "notes", length = 500)
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (totalTrades == null) totalTrades = 0;
        if (winningTrades == null) winningTrades = 0;
        if (losingTrades == null) losingTrades = 0;
        if (dailyLimitReached == null) dailyLimitReached = false;
        if (tradingStopped == null) tradingStopped = false;
    }

    /**
     * Calculate win rate
     */
    public void calculateWinRate() {
        if (totalTrades != null && totalTrades > 0) {
            this.winRate = BigDecimal.valueOf(winningTrades)
                .divide(BigDecimal.valueOf(totalTrades), 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }
    }

    /**
     * Calculate profit factor
     */
    public void calculateProfitFactor() {
        if (totalLoss != null && totalLoss.compareTo(BigDecimal.ZERO) > 0) {
            this.profitFactor = totalProfit.divide(totalLoss.abs(), 4, BigDecimal.ROUND_HALF_UP);
        }
    }

    /**
     * Calculate net PnL percentage
     */
    public void calculateNetPnlPercent() {
        if (startingBalance != null && startingBalance.compareTo(BigDecimal.ZERO) > 0 && netPnl != null) {
            this.netPnlPercent = netPnl.divide(startingBalance, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }
    }

    /**
     * Update all calculated fields
     */
    public void recalculateMetrics() {
        calculateWinRate();
        calculateProfitFactor();
        calculateNetPnlPercent();
    }
}
