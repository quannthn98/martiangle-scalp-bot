package com.bot.gift.x1000.martiangletradingbot.repository;

import com.bot.gift.x1000.martiangletradingbot.model.entity.Candle;
import com.bot.gift.x1000.martiangletradingbot.model.enums.Timeframe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Candle entity
 */
@Repository
public interface CandleRepository extends JpaRepository<Candle, Long> {

    /**
     * Find candle by symbol, timeframe and open time
     */
    Optional<Candle> findBySymbolAndTimeframeAndOpenTime(
        String symbol, Timeframe timeframe, Instant openTime
    );

    /**
     * Find latest N candles for a symbol and timeframe
     */
    @Query("SELECT c FROM Candle c WHERE c.symbol = :symbol AND c.timeframe = :timeframe " +
        "ORDER BY c.openTime DESC")
    List<Candle> findLatestCandles(
        @Param("symbol") String symbol,
        @Param("timeframe") Timeframe timeframe,
        org.springframework.data.domain.Pageable pageable
    );

    /**
     * Find candles within a time range
     */
    @Query("SELECT c FROM Candle c WHERE c.symbol = :symbol AND c.timeframe = :timeframe " +
        "AND c.openTime >= :startTime AND c.openTime <= :endTime " +
        "ORDER BY c.openTime ASC")
    List<Candle> findCandlesInRange(
        @Param("symbol") String symbol,
        @Param("timeframe") Timeframe timeframe,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    /**
     * Delete old candles
     */
    void deleteByOpenTimeBefore(Instant cutoffTime);

    /**
     * Count candles for symbol and timeframe
     */
    long countBySymbolAndTimeframe(String symbol, Timeframe timeframe);
}
