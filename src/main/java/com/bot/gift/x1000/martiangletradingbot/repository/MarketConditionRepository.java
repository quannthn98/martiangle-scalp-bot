package com.bot.gift.x1000.martiangletradingbot.repository;

import com.bot.gift.x1000.martiangletradingbot.model.entity.MarketCondition;
import com.bot.gift.x1000.martiangletradingbot.model.enums.Timeframe;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TrendDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for MarketCondition entity
 */
@Repository
public interface MarketConditionRepository extends JpaRepository<MarketCondition, Long> {

    /**
     * Find latest market condition for symbol and timeframe
     */
    @Query("SELECT mc FROM MarketCondition mc WHERE mc.symbol = :symbol AND mc.timeframe = :timeframe " +
        "ORDER BY mc.timestamp DESC LIMIT 1")
    Optional<MarketCondition> findLatestBySymbolAndTimeframe(
        @Param("symbol") String symbol,
        @Param("timeframe") Timeframe timeframe
    );

    /**
     * Find all tradeable symbols for a timeframe
     */
    @Query("SELECT mc FROM MarketCondition mc WHERE mc.timeframe = :timeframe " +
        "AND mc.isTradeable = true AND mc.timestamp >= :since " +
        "ORDER BY mc.timestamp DESC")
    List<MarketCondition> findTradeableSymbols(
        @Param("timeframe") Timeframe timeframe,
        @Param("since") Instant since
    );

    /**
     * Find symbols with specific trend
     */
    @Query("SELECT mc FROM MarketCondition mc WHERE mc.timeframe = :timeframe " +
        "AND mc.trendDirection = :trend AND mc.isTradeable = true " +
        "AND mc.timestamp >= :since ORDER BY mc.adx DESC")
    List<MarketCondition> findSymbolsByTrend(
        @Param("timeframe") Timeframe timeframe,
        @Param("trend") TrendDirection trend,
        @Param("since") Instant since
    );

    /**
     * Delete old market conditions
     */
    void deleteByTimestampBefore(Instant cutoffTime);

    /**
     * Find recent conditions for symbol
     */
    @Query("SELECT mc FROM MarketCondition mc WHERE mc.symbol = :symbol " +
        "ORDER BY mc.timestamp DESC")
    List<MarketCondition> findRecentConditionsBySymbol(
        @Param("symbol") String symbol,
        org.springframework.data.domain.Pageable pageable
    );
}
