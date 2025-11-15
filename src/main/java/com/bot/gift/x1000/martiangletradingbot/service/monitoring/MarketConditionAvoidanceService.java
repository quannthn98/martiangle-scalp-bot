package com.bot.gift.x1000.martiangletradingbot.service.monitoring;

import com.bot.gift.x1000.martiangletradingbot.config.StrategyProperties;
import com.bot.gift.x1000.martiangletradingbot.model.entity.Candle;
import com.bot.gift.x1000.martiangletradingbot.model.enums.Timeframe;
import com.bot.gift.x1000.martiangletradingbot.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Market Condition Avoidance Service
 * Monitors dangerous market conditions and prevents trading during:
 * - Extreme BTC volatility
 * - Exchange maintenance windows
 * - Major news events
 * - Weekend/low liquidity periods
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MarketConditionAvoidanceService {

    private final CandleRepository candleRepository;
    private final StrategyProperties strategyProperties;

    // Cache for known maintenance windows
    private final Set<MaintenanceWindow> maintenanceWindows = ConcurrentHashMap.newKeySet();

    // Cache for manual trading pause
    private volatile boolean manualPauseEnabled = false;
    private volatile String pauseReason = "";

    /**
     * Check if it's safe to trade based on current market conditions
     *
     * @return ConditionCheck with safety status and reason
     */
    public ConditionCheck isSafeToTrade() {
        // Check manual pause
        if (manualPauseEnabled) {
            return new ConditionCheck(false, "Manual pause enabled: " + pauseReason);
        }

        // Check BTC volatility
        ConditionCheck btcCheck = checkBtcVolatility();
        if (!btcCheck.isSafe()) {
            return btcCheck;
        }

        // Check exchange maintenance
        ConditionCheck maintenanceCheck = checkMaintenanceWindow();
        if (!maintenanceCheck.isSafe()) {
            return maintenanceCheck;
        }

        // Check weekend/low liquidity periods
        ConditionCheck liquidityCheck = checkLiquidityPeriod();
        if (!liquidityCheck.isSafe()) {
            return liquidityCheck;
        }

        // Check market hours (avoid major news times)
        ConditionCheck newsCheck = checkNewsTimePeriods();
        if (!newsCheck.isSafe()) {
            return newsCheck;
        }

        return new ConditionCheck(true, "All conditions safe");
    }

    /**
     * Check BTC volatility
     * Pauses trading if BTC moves >2% in 1 minute or >5% in 5 minutes
     */
    private ConditionCheck checkBtcVolatility() {
        try {
            // Check 1-minute BTC volatility
            List<Candle> btc1mCandles = candleRepository.findRecentCandles("BTCUSDT", Timeframe.M1, 5);

            if (btc1mCandles.size() >= 2) {
                Candle latest = btc1mCandles.get(btc1mCandles.size() - 1);
                Candle previous = btc1mCandles.get(btc1mCandles.size() - 2);

                BigDecimal priceChange = latest.getClose().subtract(previous.getClose())
                    .divide(previous.getClose(), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .abs();

                BigDecimal threshold1m = strategyProperties.getExit().getEmergency().getBtcDumpPumpPercent();

                if (priceChange.compareTo(threshold1m) > 0) {
                    log.warn("BTC high volatility detected: {}% in 1 minute", priceChange);
                    return new ConditionCheck(false,
                        String.format("BTC volatility too high: %.2f%% in 1 minute (threshold: %.2f%%)",
                            priceChange, threshold1m));
                }
            }

            // Check 5-minute BTC volatility
            if (btc1mCandles.size() >= 5) {
                BigDecimal openPrice = btc1mCandles.get(0).getOpen();
                BigDecimal closePrice = btc1mCandles.get(btc1mCandles.size() - 1).getClose();

                BigDecimal priceChange5m = closePrice.subtract(openPrice)
                    .divide(openPrice, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .abs();

                if (priceChange5m.compareTo(BigDecimal.valueOf(5.0)) > 0) {
                    log.warn("BTC high volatility detected: {}% in 5 minutes", priceChange5m);
                    return new ConditionCheck(false,
                        String.format("BTC volatility too high: %.2f%% in 5 minutes", priceChange5m));
                }
            }

            // Check volume spike indicating unusual market activity
            if (btc1mCandles.size() >= 20) {
                List<BigDecimal> volumes = btc1mCandles.stream()
                    .map(Candle::getVolume)
                    .toList();

                BigDecimal avgVolume = volumes.subList(0, volumes.size() - 1).stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(volumes.size() - 1), 8, RoundingMode.HALF_UP);

                BigDecimal latestVolume = volumes.get(volumes.size() - 1);
                BigDecimal spikeMultiplier = strategyProperties.getExit().getEmergency().getVolumeSpikeMultiplier();

                if (latestVolume.compareTo(avgVolume.multiply(spikeMultiplier)) > 0) {
                    log.warn("BTC volume spike detected: {}x average",
                        latestVolume.divide(avgVolume, 2, RoundingMode.HALF_UP));
                    return new ConditionCheck(false, "BTC volume spike detected - unusual market activity");
                }
            }

        } catch (Exception e) {
            log.error("Error checking BTC volatility: {}", e.getMessage());
            // Fail-safe: pause trading if we can't determine BTC volatility
            return new ConditionCheck(false, "Unable to determine BTC volatility - trading paused for safety");
        }

        return new ConditionCheck(true, "BTC volatility normal");
    }

    /**
     * Check if we're in a known maintenance window
     */
    private ConditionCheck checkMaintenanceWindow() {
        Instant now = Instant.now();

        for (MaintenanceWindow window : maintenanceWindows) {
            if (now.isAfter(window.start()) && now.isBefore(window.end())) {
                log.warn("In maintenance window: {} - {}", window.start(), window.end());
                return new ConditionCheck(false,
                    String.format("Exchange maintenance: %s", window.description()));
            }
        }

        return new ConditionCheck(true, "No maintenance window");
    }

    /**
     * Check for low liquidity periods
     * Avoids weekends and off-hours
     */
    private ConditionCheck checkLiquidityPeriod() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
        DayOfWeek dayOfWeek = nowUtc.getDayOfWeek();
        LocalTime time = nowUtc.toLocalTime();

        // Avoid Saturday and Sunday (crypto markets never close, but liquidity is lower)
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            log.debug("Weekend detected - lower liquidity period");
            // Note: For crypto, this is optional as markets are 24/7
            // Uncomment to pause on weekends:
            // return new ConditionCheck(false, "Weekend - lower liquidity period");
        }

        // Avoid very late/early hours (00:00 - 04:00 UTC) - lowest liquidity
        if (time.isAfter(LocalTime.of(0, 0)) && time.isBefore(LocalTime.of(4, 0))) {
            log.debug("Low liquidity hours (00:00-04:00 UTC)");
            // Uncomment to pause during low liquidity hours:
            // return new ConditionCheck(false, "Low liquidity hours (00:00-04:00 UTC)");
        }

        return new ConditionCheck(true, "Normal liquidity period");
    }

    /**
     * Check for major news event periods
     * Typically: Fed meetings, NFP, CPI, major crypto events
     */
    private ConditionCheck checkNewsTimePeriods() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
        LocalTime time = nowUtc.toLocalTime();
        DayOfWeek dayOfWeek = nowUtc.getDayOfWeek();

        // US market open (13:30 UTC) - high volatility period
        if (time.isAfter(LocalTime.of(13, 20)) && time.isBefore(LocalTime.of(14, 0))) {
            log.debug("US market open period - potential volatility");
            // Uncomment to pause during US open:
            // return new ConditionCheck(false, "US market open - high volatility period");
        }

        // First Friday of month - NFP (Non-Farm Payroll) - usually 12:30 UTC
        // This is a placeholder - in production, integrate with economic calendar API
        if (dayOfWeek == DayOfWeek.FRIDAY && isFirstWeekOfMonth(nowUtc)) {
            if (time.isAfter(LocalTime.of(12, 20)) && time.isBefore(LocalTime.of(13, 0))) {
                log.warn("Potential NFP release time");
                // Uncomment to pause during NFP:
                // return new ConditionCheck(false, "NFP release period - avoid trading");
            }
        }

        return new ConditionCheck(true, "No major news events detected");
    }

    /**
     * Check if current date is in the first week of the month
     */
    private boolean isFirstWeekOfMonth(ZonedDateTime date) {
        return date.getDayOfMonth() <= 7;
    }

    /**
     * Manually pause trading
     */
    public void enableManualPause(String reason) {
        this.manualPauseEnabled = true;
        this.pauseReason = reason;
        log.warn("Manual trading pause enabled: {}", reason);
    }

    /**
     * Resume trading after manual pause
     */
    public void disableManualPause() {
        this.manualPauseEnabled = false;
        this.pauseReason = "";
        log.info("Manual trading pause disabled - resuming normal operations");
    }

    /**
     * Check if manual pause is active
     */
    public boolean isManualPauseEnabled() {
        return manualPauseEnabled;
    }

    /**
     * Add a maintenance window
     */
    public void addMaintenanceWindow(Instant start, Instant end, String description) {
        MaintenanceWindow window = new MaintenanceWindow(start, end, description);
        maintenanceWindows.add(window);
        log.info("Added maintenance window: {} to {} - {}", start, end, description);
    }

    /**
     * Remove expired maintenance windows
     */
    public void cleanupExpiredMaintenanceWindows() {
        Instant now = Instant.now();
        maintenanceWindows.removeIf(window -> window.end().isBefore(now));
    }

    /**
     * Get all active maintenance windows
     */
    public Set<MaintenanceWindow> getActiveMaintenanceWindows() {
        Instant now = Instant.now();
        Set<MaintenanceWindow> active = new HashSet<>();

        for (MaintenanceWindow window : maintenanceWindows) {
            if (now.isBefore(window.end())) {
                active.add(window);
            }
        }

        return active;
    }

    /**
     * Check if a specific symbol should be avoided
     * Can be extended to check for symbol-specific news events
     */
    public ConditionCheck isSymbolSafeToTrade(String symbol) {
        // Placeholder for symbol-specific checks
        // In production, integrate with news APIs or maintain a blacklist

        // Example: Avoid trading pairs during major token unlocks, network upgrades, etc.
        // if (symbol.equals("ETHUSDT") && isEthereumUpgradeDay()) {
        //     return new ConditionCheck(false, "Ethereum network upgrade in progress");
        // }

        return new ConditionCheck(true, "Symbol safe to trade");
    }

    /**
     * Condition check result
     */
    public record ConditionCheck(
        boolean isSafe,
        String reason
    ) {}

    /**
     * Maintenance window record
     */
    public record MaintenanceWindow(
        Instant start,
        Instant end,
        String description
    ) {}
}
