package com.bot.gift.x1000.martiangletradingbot.backtest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Backtest Result
 * Contains all performance metrics from a backtest run
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResult {

    // Backtest metadata
    private String symbol;
    private Instant startTime;
    private Instant endTime;
    private BigDecimal initialBalance;
    private BigDecimal finalBalance;

    // Trade statistics
    @Builder.Default
    private List<BacktestTrade> trades = new ArrayList<>();
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;

    // Performance metrics
    private BigDecimal winRate; // Percentage
    private BigDecimal profitFactor; // Gross profit / Gross loss
    private BigDecimal averageWin;
    private BigDecimal averageLoss;
    private BigDecimal largestWin;
    private BigDecimal largestLoss;
    private BigDecimal averageRMultiple;

    // Returns
    private BigDecimal totalReturn; // USD
    private BigDecimal totalReturnPercent;
    private BigDecimal netProfit; // After fees and slippage

    // Drawdown metrics
    private BigDecimal maxDrawdown; // Percentage
    private BigDecimal maxDrawdownUsd;
    private Instant maxDrawdownDate;

    // Risk-adjusted returns
    private BigDecimal sharpeRatio;
    private BigDecimal sortinoRatio;

    // Trade duration
    private Long averageHoldingTimeSeconds;
    private Long maxHoldingTimeSeconds;
    private Long minHoldingTimeSeconds;

    // Fee analysis
    private BigDecimal totalFees;
    private BigDecimal totalSlippage;

    // Consecutive trades
    private int maxConsecutiveWins;
    private int maxConsecutiveLosses;

    /**
     * Calculate all performance metrics
     */
    public void calculateMetrics() {
        if (trades.isEmpty()) {
            return;
        }

        this.totalTrades = trades.size();
        this.winningTrades = (int) trades.stream().filter(BacktestTrade::isWinner).count();
        this.losingTrades = totalTrades - winningTrades;

        // Win rate
        this.winRate = BigDecimal.valueOf(winningTrades)
            .divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        // Calculate wins and losses
        BigDecimal grossProfit = trades.stream()
            .filter(BacktestTrade::isWinner)
            .map(BacktestTrade::getNetProfitLoss)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grossLoss = trades.stream()
            .filter(t -> !t.isWinner())
            .map(BacktestTrade::getNetProfitLoss)
            .map(BigDecimal::abs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Profit factor
        if (grossLoss.compareTo(BigDecimal.ZERO) > 0) {
            this.profitFactor = grossProfit.divide(grossLoss, 2, RoundingMode.HALF_UP);
        } else {
            this.profitFactor = grossProfit.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(999.99)
                : BigDecimal.ZERO;
        }

        // Average win/loss
        if (winningTrades > 0) {
            this.averageWin = grossProfit.divide(BigDecimal.valueOf(winningTrades), 2, RoundingMode.HALF_UP);
        } else {
            this.averageWin = BigDecimal.ZERO;
        }

        if (losingTrades > 0) {
            this.averageLoss = grossLoss.divide(BigDecimal.valueOf(losingTrades), 2, RoundingMode.HALF_UP);
        } else {
            this.averageLoss = BigDecimal.ZERO;
        }

        // Largest win/loss
        this.largestWin = trades.stream()
            .filter(BacktestTrade::isWinner)
            .map(BacktestTrade::getNetProfitLoss)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        this.largestLoss = trades.stream()
            .filter(t -> !t.isWinner())
            .map(BacktestTrade::getNetProfitLoss)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        // Total return
        this.netProfit = grossProfit.subtract(grossLoss);
        this.totalReturn = finalBalance.subtract(initialBalance);
        this.totalReturnPercent = totalReturn
            .divide(initialBalance, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        // Average R-multiple
        BigDecimal totalRMultiple = trades.stream()
            .map(BacktestTrade::getRMultiple)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.averageRMultiple = totalRMultiple.divide(
            BigDecimal.valueOf(totalTrades), 2, RoundingMode.HALF_UP
        );

        // Fees and slippage
        this.totalFees = trades.stream()
            .map(BacktestTrade::getFees)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalSlippage = trades.stream()
            .map(BacktestTrade::getSlippage)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Holding time
        this.averageHoldingTimeSeconds = (long) trades.stream()
            .mapToLong(BacktestTrade::getHoldingTimeSeconds)
            .average()
            .orElse(0);

        this.maxHoldingTimeSeconds = trades.stream()
            .mapToLong(BacktestTrade::getHoldingTimeSeconds)
            .max()
            .orElse(0);

        this.minHoldingTimeSeconds = trades.stream()
            .mapToLong(BacktestTrade::getHoldingTimeSeconds)
            .min()
            .orElse(0);

        // Calculate consecutive wins/losses
        calculateConsecutiveStats();
    }

    /**
     * Calculate consecutive wins and losses
     */
    private void calculateConsecutiveStats() {
        int currentWinStreak = 0;
        int currentLossStreak = 0;
        this.maxConsecutiveWins = 0;
        this.maxConsecutiveLosses = 0;

        for (BacktestTrade trade : trades) {
            if (trade.isWinner()) {
                currentWinStreak++;
                currentLossStreak = 0;
                maxConsecutiveWins = Math.max(maxConsecutiveWins, currentWinStreak);
            } else {
                currentLossStreak++;
                currentWinStreak = 0;
                maxConsecutiveLosses = Math.max(maxConsecutiveLosses, currentLossStreak);
            }
        }
    }

    /**
     * Check if backtest meets success criteria
     */
    public boolean meetsSuccessCriteria() {
        // Criteria: Win rate > 55%, Profit factor > 1.5, Max drawdown < 15%
        boolean winRateOk = winRate != null && winRate.compareTo(BigDecimal.valueOf(55)) > 0;
        boolean profitFactorOk = profitFactor != null && profitFactor.compareTo(BigDecimal.valueOf(1.5)) > 0;
        boolean drawdownOk = maxDrawdown != null && maxDrawdown.compareTo(BigDecimal.valueOf(15)) < 0;

        return winRateOk && profitFactorOk && drawdownOk;
    }

    /**
     * Generate summary report
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("═══════════════════════════════════════════════════════════════\n");
        report.append("                    BACKTEST RESULTS\n");
        report.append("═══════════════════════════════════════════════════════════════\n\n");

        report.append(String.format("Symbol: %s\n", symbol));
        report.append(String.format("Period: %s to %s\n", startTime, endTime));
        report.append(String.format("Initial Balance: $%.2f\n", initialBalance));
        report.append(String.format("Final Balance: $%.2f\n", finalBalance));
        report.append("\n");

        report.append("PERFORMANCE METRICS\n");
        report.append("───────────────────────────────────────────────────────────────\n");
        report.append(String.format("Total Trades: %d\n", totalTrades));
        report.append(String.format("Winning Trades: %d (%.1f%%)\n", winningTrades, winRate));
        report.append(String.format("Losing Trades: %d\n", losingTrades));
        report.append(String.format("Win Rate: %.2f%% %s\n", winRate,
            winRate.compareTo(BigDecimal.valueOf(55)) > 0 ? "✓" : "✗"));
        report.append(String.format("Profit Factor: %.2f %s\n", profitFactor,
            profitFactor.compareTo(BigDecimal.valueOf(1.5)) > 0 ? "✓" : "✗"));
        report.append("\n");

        report.append("PROFIT/LOSS\n");
        report.append("───────────────────────────────────────────────────────────────\n");
        report.append(String.format("Net Profit: $%.2f (%.2f%%)\n", netProfit, totalReturnPercent));
        report.append(String.format("Average Win: $%.2f\n", averageWin));
        report.append(String.format("Average Loss: $%.2f\n", averageLoss));
        report.append(String.format("Largest Win: $%.2f\n", largestWin));
        report.append(String.format("Largest Loss: $%.2f\n", largestLoss));
        report.append(String.format("Average R-Multiple: %.2fR\n", averageRMultiple));
        report.append("\n");

        report.append("RISK METRICS\n");
        report.append("───────────────────────────────────────────────────────────────\n");
        report.append(String.format("Max Drawdown: %.2f%% ($%.2f) %s\n",
            maxDrawdown, maxDrawdownUsd,
            maxDrawdown.compareTo(BigDecimal.valueOf(15)) < 0 ? "✓" : "✗"));
        if (sharpeRatio != null) {
            report.append(String.format("Sharpe Ratio: %.2f\n", sharpeRatio));
        }
        report.append("\n");

        report.append("TRADE DURATION\n");
        report.append("───────────────────────────────────────────────────────────────\n");
        report.append(String.format("Average Hold: %d seconds (%.1f min)\n",
            averageHoldingTimeSeconds, averageHoldingTimeSeconds / 60.0));
        report.append(String.format("Max Hold: %d seconds (%.1f min)\n",
            maxHoldingTimeSeconds, maxHoldingTimeSeconds / 60.0));
        report.append(String.format("Min Hold: %d seconds (%.1f min)\n",
            minHoldingTimeSeconds, minHoldingTimeSeconds / 60.0));
        report.append("\n");

        report.append("COSTS\n");
        report.append("───────────────────────────────────────────────────────────────\n");
        report.append(String.format("Total Fees: $%.2f\n", totalFees));
        report.append(String.format("Total Slippage: $%.2f\n", totalSlippage));
        report.append("\n");

        report.append("CONSISTENCY\n");
        report.append("───────────────────────────────────────────────────────────────\n");
        report.append(String.format("Max Consecutive Wins: %d\n", maxConsecutiveWins));
        report.append(String.format("Max Consecutive Losses: %d\n", maxConsecutiveLosses));
        report.append("\n");

        report.append("═══════════════════════════════════════════════════════════════\n");
        report.append(String.format("OVERALL: %s\n",
            meetsSuccessCriteria() ? "✓ PASSED ALL CRITERIA" : "✗ FAILED CRITERIA"));
        report.append("═══════════════════════════════════════════════════════════════\n");

        return report.toString();
    }
}
