package com.bot.gift.x1000.martiangletradingbot.repository;

import com.bot.gift.x1000.martiangletradingbot.model.entity.DailyPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for DailyPerformance entity
 */
@Repository
public interface DailyPerformanceRepository extends JpaRepository<DailyPerformance, Long> {

    /**
     * Find daily performance by date
     */
    Optional<DailyPerformance> findByTradingDate(LocalDate tradingDate);

    /**
     * Find performance within date range
     */
    @Query("SELECT dp FROM DailyPerformance dp WHERE dp.tradingDate >= :startDate " +
        "AND dp.tradingDate <= :endDate ORDER BY dp.tradingDate DESC")
    List<DailyPerformance> findByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find last N days of performance
     */
    @Query("SELECT dp FROM DailyPerformance dp ORDER BY dp.tradingDate DESC")
    List<DailyPerformance> findRecentPerformance(org.springframework.data.domain.Pageable pageable);

    /**
     * Check if daily limit reached for today
     */
    @Query("SELECT dp.dailyLimitReached FROM DailyPerformance dp " +
        "WHERE dp.tradingDate = :date")
    Optional<Boolean> isDailyLimitReached(@Param("date") LocalDate date);

    /**
     * Get total PnL for date range
     */
    @Query("SELECT SUM(dp.netPnl) FROM DailyPerformance dp " +
        "WHERE dp.tradingDate >= :startDate AND dp.tradingDate <= :endDate")
    Optional<java.math.BigDecimal> getTotalPnlForRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
