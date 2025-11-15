package com.bot.gift.x1000.martiangletradingbot.repository;

import com.bot.gift.x1000.martiangletradingbot.model.entity.Position;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Position entity
 */
@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {

    /**
     * Find position by trade ID
     */
    Optional<Position> findByTradeId(String tradeId);

    /**
     * Find all open positions
     */
    List<Position> findByStatus(TradeStatus status);

    /**
     * Find all open or partially closed positions
     */
    @Query("SELECT p FROM Position p WHERE p.status IN ('OPEN', 'PARTIALLY_CLOSED')")
    List<Position> findAllActivePositions();

    /**
     * Count active positions
     */
    @Query("SELECT COUNT(p) FROM Position p WHERE p.status IN ('OPEN', 'PARTIALLY_CLOSED')")
    long countActivePositions();

    /**
     * Calculate total exposure
     */
    @Query("SELECT COALESCE(SUM(p.remainingPositionSize * p.currentPrice), 0) " +
        "FROM Position p WHERE p.status IN ('OPEN', 'PARTIALLY_CLOSED')")
    BigDecimal calculateTotalExposure();

    /**
     * Find positions that need DCA entry
     */
    @Query("SELECT p FROM Position p WHERE p.status = 'OPEN' " +
        "AND p.dcaLevel < p.maxDcaLevel " +
        "AND p.currentPrice <= :price")
    List<Position> findPositionsNeedingDcaEntry(@Param("price") BigDecimal price);

    /**
     * Find positions held longer than specified minutes
     */
    @Query("SELECT p FROM Position p WHERE p.status IN ('OPEN', 'PARTIALLY_CLOSED') " +
        "AND p.entryTime <= :cutoffTime")
    List<Position> findPositionsOlderThan(@Param("cutoffTime") Instant cutoffTime);

    /**
     * Delete closed positions
     */
    void deleteByStatus(TradeStatus status);
}
