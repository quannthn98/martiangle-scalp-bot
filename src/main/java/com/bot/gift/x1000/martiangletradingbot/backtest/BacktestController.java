package com.bot.gift.x1000.martiangletradingbot.backtest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Backtest Controller
 * REST API for running backtests and viewing results
 */
@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final BacktestingEngine backtestingEngine;
    private final HistoricalDataLoader dataLoader;

    /**
     * Run a backtest
     *
     * POST /api/backtest/run
     * {
     *   "symbol": "BTCUSDT",
     *   "startTime": "2024-01-01T00:00:00Z",
     *   "endTime": "2024-03-31T23:59:59Z",
     *   "initialBalance": 10000
     * }
     */
    @PostMapping("/run")
    public ResponseEntity<BacktestResult> runBacktest(@RequestBody BacktestRequest request) {
        log.info("Running backtest for {} from {} to {}", request.getSymbol(),
            request.getStartTime(), request.getEndTime());

        try {
            BacktestResult result = backtestingEngine.runBacktest(
                request.getSymbol(),
                request.getStartTime(),
                request.getEndTime(),
                request.getInitialBalance()
            );

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Backtest failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Run a quick backtest with default parameters (last 30 days)
     *
     * GET /api/backtest/quick?symbol=BTCUSDT
     */
    @GetMapping("/quick")
    public ResponseEntity<BacktestResult> quickBacktest(
        @RequestParam(defaultValue = "BTCUSDT") String symbol,
        @RequestParam(defaultValue = "10000") BigDecimal initialBalance
    ) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(30, ChronoUnit.DAYS);

        log.info("Running quick backtest for {} (last 30 days)", symbol);

        try {
            BacktestResult result = backtestingEngine.runBacktest(
                symbol, startTime, endTime, initialBalance
            );

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Quick backtest failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get backtest report as text
     *
     * POST /api/backtest/report
     */
    @PostMapping("/report")
    public ResponseEntity<String> getBacktestReport(@RequestBody BacktestRequest request) {
        log.info("Generating backtest report for {}", request.getSymbol());

        try {
            BacktestResult result = backtestingEngine.runBacktest(
                request.getSymbol(),
                request.getStartTime(),
                request.getEndTime(),
                request.getInitialBalance()
            );

            String report = result.generateReport();
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Report generation failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get sample backtest data status
     *
     * GET /api/backtest/data-status?symbol=BTCUSDT
     */
    @GetMapping("/data-status")
    public ResponseEntity<Map<String, Object>> getDataStatus(@RequestParam String symbol) {
        Map<String, Object> status = new HashMap<>();

        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(90, ChronoUnit.DAYS);

        // Check available data for each timeframe
        // This would check the database for available historical data
        // For now, return sample status

        status.put("symbol", symbol);
        status.put("availableData", "Sample data will be generated if no historical data found");
        status.put("recommendation", "Upload historical CSV data or connect to Binance API for real backtesting");

        return ResponseEntity.ok(status);
    }

    /**
     * Backtest request DTO
     */
    public static class BacktestRequest {
        private String symbol;
        private Instant startTime;
        private Instant endTime;
        private BigDecimal initialBalance;

        public BacktestRequest() {
            this.initialBalance = BigDecimal.valueOf(10000);
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public void setStartTime(Instant startTime) {
            this.startTime = startTime;
        }

        public Instant getEndTime() {
            return endTime;
        }

        public void setEndTime(Instant endTime) {
            this.endTime = endTime;
        }

        public BigDecimal getInitialBalance() {
            return initialBalance;
        }

        public void setInitialBalance(BigDecimal initialBalance) {
            this.initialBalance = initialBalance;
        }
    }
}
