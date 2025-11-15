package com.bot.gift.x1000.martiangletradingbot.repository;

import com.bot.gift.x1000.martiangletradingbot.model.entity.Trade;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Trade entity
 */
@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    /**
     * Find trade by trade ID
     */
    Optional<Trade> findByTradeId(String tradeId);

    /**
     * Find all trades with a specific status
     */
    List<Trade> findByStatus(TradeStatus status);

    /**
     * Find all open trades for a symbol
     */
    List<Trade> findBySymbolAndStatus(String symbol, TradeStatus status);

    /**
     * Find trades within date range
     */
    @Query("SELECT t FROM Trade t WHERE t.entryTime >= :startTime AND t.entryTime <= :endTime " +
        "ORDER BY t.entryTime DESC")
    List<Trade> findTradesInDateRange(
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    /**
     * Find today's trades
     */
    @Query("SELECT t FROM Trade t WHERE t.entryTime >= :todayStart ORDER BY t.entryTime DESC")
    List<Trade> findTodaysTrades(@Param("todayStart") Instant todayStart);

    /**
     * Count trades by status
     */
    long countByStatus(TradeStatus status);

    /**
     * Get performance metrics for date range
     */
    @Query("SELECT " +
        "COUNT(t) as totalTrades, " +
        "SUM(CASE WHEN t.profitLossUsdt > 0 THEN 1 ELSE 0 END) as winningTrades, " +
        "SUM(CASE WHEN t.profitLossUsdt < 0 THEN 1 ELSE 0 END) as losingTrades, " +
        "SUM(CASE WHEN t.profitLossUsdt > 0 THEN t.profitLossUsdt ELSE 0 END) as totalProfit, " +
        "SUM(CASE WHEN t.profitLossUsdt < 0 THEN t.profitLossUsdt ELSE 0 END) as totalLoss " +
        "FROM Trade t WHERE t.entryTime >= :startTime AND t.entryTime <= :endTime AND t.status = 'CLOSED'")
    Object[] getPerformanceMetrics(
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );
}
