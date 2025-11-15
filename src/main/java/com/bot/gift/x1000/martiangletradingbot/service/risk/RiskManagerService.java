package com.bot.gift.x1000.martiangletradingbot.service.risk;

import com.bot.gift.x1000.martiangletradingbot.config.RiskProperties;
import com.bot.gift.x1000.martiangletradingbot.indicator.ATRIndicator;
import com.bot.gift.x1000.martiangletradingbot.model.dto.EntrySignal;
import com.bot.gift.x1000.martiangletradingbot.model.entity.DailyPerformance;
import com.bot.gift.x1000.martiangletradingbot.repository.DailyPerformanceRepository;
import com.bot.gift.x1000.martiangletradingbot.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Risk Manager Service
 * Handles position sizing, daily limits, and exposure management
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RiskManagerService {

    private final RiskProperties riskProperties;
    private final DailyPerformanceRepository dailyPerformanceRepository;
    private final PositionRepository positionRepository;
    private final ATRIndicator atrIndicator;

    /**
     * Calculate position size based on account balance and risk parameters
     *
     * @param accountBalance Current account balance
     * @param signal Entry signal with stop loss information
     * @param atr Current ATR value for volatility adjustment
     * @return Position size in USDT
     */
    public BigDecimal calculatePositionSize(BigDecimal accountBalance, EntrySignal signal, BigDecimal atr) {
        // Calculate risk amount
        BigDecimal riskPercent = riskProperties.getRiskPerTradePercent();
        BigDecimal riskAmount = accountBalance
            .multiply(riskPercent)
            .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        // Calculate stop loss distance
        BigDecimal entryPrice = signal.getEntryPrice();
        BigDecimal stopLoss = signal.getStopLoss();
        BigDecimal stopLossDistance = entryPrice.subtract(stopLoss).abs();

        if (stopLossDistance.compareTo(BigDecimal.ZERO) == 0) {
            log.error("Stop loss distance is zero, cannot calculate position size");
            return BigDecimal.ZERO;
        }

        // Calculate stop loss percentage
        BigDecimal stopLossPercent = stopLossDistance
            .divide(entryPrice, 8, RoundingMode.HALF_UP);

        // Calculate base position size
        // Position Size = Risk Amount / Stop Loss %
        BigDecimal positionSize = riskAmount.divide(stopLossPercent, 2, RoundingMode.HALF_UP);

        // Adjust for ATR volatility if provided
        if (atr != null) {
            // Calculate normal ATR (could be stored/calculated from historical data)
            // For simplicity, we'll use a multiplier approach
            // Higher ATR = reduce position size
            positionSize = adjustPositionSizeForVolatility(positionSize, atr);
        }

        // Apply min/max limits
        positionSize = applyPositionSizeLimits(positionSize);

        log.info("Calculated position size: ${} for {} with risk amount: ${}, SL%: {}%",
            positionSize, signal.getSymbol(), riskAmount, stopLossPercent.multiply(BigDecimal.valueOf(100)));

        return positionSize;
    }

    /**
     * Adjust position size based on current ATR
     */
    private BigDecimal adjustPositionSizeForVolatility(BigDecimal baseSize, BigDecimal currentAtr) {
        // This is a simplified version
        // In production, you'd compare current ATR to historical average
        // For now, we'll use a conservative approach
        BigDecimal volatilityFactor = BigDecimal.ONE;

        // If ATR is high, reduce position size (placeholder logic)
        // You could implement more sophisticated ATR normalization here

        return baseSize.multiply(volatilityFactor).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Apply min/max position size limits
     */
    private BigDecimal applyPositionSizeLimits(BigDecimal positionSize) {
        BigDecimal minSize = riskProperties.getMinPositionSizeUsdt();
        BigDecimal maxSize = riskProperties.getMaxPositionSizeUsdt();

        if (positionSize.compareTo(minSize) < 0) {
            log.warn("Position size ${} below minimum ${}, adjusting", positionSize, minSize);
            return minSize;
        }

        if (positionSize.compareTo(maxSize) > 0) {
            log.warn("Position size ${} above maximum ${}, adjusting", positionSize, maxSize);
            return maxSize;
        }

        return positionSize;
    }

    /**
     * Check if daily loss limit has been reached
     */
    public boolean isDailyLimitReached() {
        LocalDate today = LocalDate.now();
        Optional<DailyPerformance> performance = dailyPerformanceRepository.findByTradingDate(today);

        if (performance.isEmpty()) {
            return false;
        }

        DailyPerformance dp = performance.get();

        // Check if limit flag is set
        if (Boolean.TRUE.equals(dp.getDailyLimitReached())) {
            log.warn("Daily limit flag is set for {}", today);
            return true;
        }

        // Check actual loss percentage
        if (dp.getNetPnlPercent() != null) {
            BigDecimal maxLoss = riskProperties.getMaxDailyLossPercent().negate();
            if (dp.getNetPnlPercent().compareTo(maxLoss) <= 0) {
                log.warn("Daily loss limit reached: {}% (max: {}%)", dp.getNetPnlPercent(), maxLoss);

                // Set flag
                dp.setDailyLimitReached(true);
                dp.setTradingStopped(true);
                dailyPerformanceRepository.save(dp);

                return true;
            }
        }

        return false;
    }

    /**
     * Check if max daily trades limit has been reached
     */
    public boolean isMaxTradesReached() {
        LocalDate today = LocalDate.now();
        Optional<DailyPerformance> performance = dailyPerformanceRepository.findByTradingDate(today);

        if (performance.isEmpty()) {
            return false;
        }

        DailyPerformance dp = performance.get();
        int maxTrades = riskProperties.getMaxDailyTrades();

        if (dp.getTotalTrades() != null && dp.getTotalTrades() >= maxTrades) {
            log.warn("Max daily trades reached: {} (max: {})", dp.getTotalTrades(), maxTrades);
            return true;
        }

        return false;
    }

    /**
     * Check if max concurrent trades limit has been reached
     */
    public boolean isMaxConcurrentTradesReached() {
        long activePositions = positionRepository.countActivePositions();
        int maxConcurrent = riskProperties.getMaxConcurrentTrades();

        if (activePositions >= maxConcurrent) {
            log.warn("Max concurrent trades reached: {} (max: {})", activePositions, maxConcurrent);
            return true;
        }

        return false;
    }

    /**
     * Check if total exposure limit has been reached
     */
    public boolean isMaxExposureReached(BigDecimal accountBalance) {
        BigDecimal totalExposure = positionRepository.calculateTotalExposure();
        BigDecimal maxExposure = accountBalance
            .multiply(riskProperties.getMaxTotalExposurePercent())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        if (totalExposure.compareTo(maxExposure) >= 0) {
            log.warn("Max exposure reached: ${} (max: ${})", totalExposure, maxExposure);
            return true;
        }

        return false;
    }

    /**
     * Check if a new trade can be opened
     */
    public boolean canOpenNewTrade(BigDecimal accountBalance) {
        if (isDailyLimitReached()) {
            log.warn("Cannot open new trade: daily loss limit reached");
            return false;
        }

        if (isMaxTradesReached()) {
            log.warn("Cannot open new trade: max daily trades reached");
            return false;
        }

        if (isMaxConcurrentTradesReached()) {
            log.warn("Cannot open new trade: max concurrent trades reached");
            return false;
        }

        if (isMaxExposureReached(accountBalance)) {
            log.warn("Cannot open new trade: max exposure reached");
            return false;
        }

        return true;
    }

    /**
     * Calculate DCA entry price for next level
     *
     * @param currentEntry Current entry price
     * @param direction Trade direction
     * @param spacingPercent Spacing percentage
     * @return Next DCA entry price
     */
    public BigDecimal calculateDcaEntryPrice(BigDecimal currentEntry, String direction,
                                            BigDecimal spacingPercent) {
        BigDecimal spacing = currentEntry
            .multiply(spacingPercent)
            .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        if ("LONG".equals(direction)) {
            // For LONG, DCA entries are below current price
            return currentEntry.subtract(spacing);
        } else {
            // For SHORT, DCA entries are above current price
            return currentEntry.add(spacing);
        }
    }

    /**
     * Check if DCA entry should be triggered
     *
     * @param currentPrice Current market price
     * @param dcaPrice DCA trigger price
     * @param direction Trade direction
     * @return true if DCA should be triggered
     */
    public boolean shouldTriggerDca(BigDecimal currentPrice, BigDecimal dcaPrice, String direction) {
        if ("LONG".equals(direction)) {
            // For LONG, trigger when price reaches or falls below DCA price
            return currentPrice.compareTo(dcaPrice) <= 0;
        } else {
            // For SHORT, trigger when price reaches or rises above DCA price
            return currentPrice.compareTo(dcaPrice) >= 0;
        }
    }

    /**
     * Get recommended leverage based on risk settings
     */
    public int getRecommendedLeverage() {
        return riskProperties.getDefaultLeverage();
    }

    /**
     * Calculate maximum allowed leverage
     */
    public int getMaxLeverage() {
        return riskProperties.getMaxLeverage();
    }
}
