package com.bot.gift.x1000.martiangletradingbot.backtest;

import com.bot.gift.x1000.martiangletradingbot.config.StrategyProperties;
import com.bot.gift.x1000.martiangletradingbot.model.dto.EntrySignal;
import com.bot.gift.x1000.martiangletradingbot.model.dto.TrendAnalysis;
import com.bot.gift.x1000.martiangletradingbot.model.entity.Candle;
import com.bot.gift.x1000.martiangletradingbot.model.enums.Timeframe;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeDirection;
import com.bot.gift.x1000.martiangletradingbot.service.risk.RiskManagerService;
import com.bot.gift.x1000.martiangletradingbot.service.scanner.MarketScannerService;
import com.bot.gift.x1000.martiangletradingbot.service.signal.SignalDetectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backtesting Engine
 * Simulates trading strategy on historical data with realistic execution
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BacktestingEngine {

    private final MarketScannerService marketScannerService;
    private final SignalDetectorService signalDetectorService;
    private final RiskManagerService riskManagerService;
    private final StrategyProperties strategyProperties;
    private final HistoricalDataLoader dataLoader;

    // Simulation parameters
    private static final BigDecimal MAKER_FEE = BigDecimal.valueOf(0.0002); // 0.02%
    private static final BigDecimal TAKER_FEE = BigDecimal.valueOf(0.0004); // 0.04%
    private static final BigDecimal MIN_SLIPPAGE = BigDecimal.valueOf(0.0005); // 0.05%
    private static final BigDecimal MAX_SLIPPAGE = BigDecimal.valueOf(0.001); // 0.1%

    /**
     * Run backtest on historical data
     *
     * @param symbol Trading pair symbol
     * @param startTime Start time for backtest
     * @param endTime End time for backtest
     * @param initialBalance Starting balance
     * @return Backtest results
     */
    public BacktestResult runBacktest(String symbol, Instant startTime, Instant endTime,
                                       BigDecimal initialBalance) {
        log.info("Starting backtest for {} from {} to {}", symbol, startTime, endTime);

        // Load historical data for all timeframes
        Map<Timeframe, List<Candle>> candleData = loadHistoricalData(symbol, startTime, endTime);

        // Initialize backtest state
        BacktestState state = new BacktestState();
        state.symbol = symbol;
        state.balance = initialBalance;
        state.initialBalance = initialBalance;
        state.equity = initialBalance;
        state.peakEquity = initialBalance;
        state.maxDrawdown = BigDecimal.ZERO;

        // Get 1-minute candles for iteration (finest granularity)
        List<Candle> m1Candles = candleData.get(Timeframe.M1);
        if (m1Candles == null || m1Candles.isEmpty()) {
            log.error("No 1-minute candles available for backtest");
            return createEmptyResult(symbol, startTime, endTime, initialBalance);
        }

        log.info("Processing {} 1-minute candles", m1Candles.size());

        // Main backtest loop - iterate through each 1-minute candle
        for (int i = 200; i < m1Candles.size(); i++) { // Start at 200 to have enough history for indicators
            Candle currentCandle = m1Candles.get(i);
            BigDecimal currentPrice = currentCandle.getClose();
            Instant currentTime = currentCandle.getCloseTime();

            // Update existing position if open
            if (state.currentPosition != null) {
                updatePosition(state, currentPrice, currentTime, candleData, i);
            }

            // Check for new trade signals (only if no position open and not on cooldown)
            if (state.currentPosition == null && !isOnTradeCooldown(state, currentTime)) {
                checkForEntrySignal(state, candleData, i, currentPrice, currentTime);
            }

            // Update equity and drawdown tracking
            state.equity = state.balance;
            if (state.currentPosition != null) {
                state.equity = state.equity.add(
                    state.currentPosition.calculateUnrealizedPnl(currentPrice)
                );
            }

            // Track peak equity and drawdown
            if (state.equity.compareTo(state.peakEquity) > 0) {
                state.peakEquity = state.equity;
            }

            BigDecimal currentDrawdown = state.peakEquity.subtract(state.equity)
                .divide(state.peakEquity, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

            if (currentDrawdown.compareTo(state.maxDrawdown) > 0) {
                state.maxDrawdown = currentDrawdown;
                state.maxDrawdownDate = currentTime;
                state.maxDrawdownUsd = state.peakEquity.subtract(state.equity);
            }

            // Progress logging
            if (i % 10000 == 0) {
                log.info("Backtest progress: {}/{} candles, Trades: {}, Balance: ${}",
                    i, m1Candles.size(), state.completedTrades.size(), state.balance);
            }
        }

        // Close any remaining open position at the end
        if (state.currentPosition != null) {
            BigDecimal finalPrice = m1Candles.get(m1Candles.size() - 1).getClose();
            Instant finalTime = m1Candles.get(m1Candles.size() - 1).getCloseTime();
            closePosition(state, finalPrice, finalTime, "BACKTEST_END");
        }

        // Generate results
        return generateResults(state, symbol, startTime, endTime);
    }

    /**
     * Load historical candle data for all required timeframes
     */
    private Map<Timeframe, List<Candle>> loadHistoricalData(String symbol, Instant startTime, Instant endTime) {
        Map<Timeframe, List<Candle>> data = new HashMap<>();

        // Try to load from database first, fallback to sample data
        List<Candle> m1Candles = dataLoader.loadFromDatabase(symbol, Timeframe.M1, startTime, endTime);
        if (m1Candles.isEmpty()) {
            log.warn("No M1 data in database, generating sample data");
            m1Candles = dataLoader.generateSampleData(symbol, Timeframe.M1, 50000, 50000, 1);
        }

        List<Candle> h1Candles = dataLoader.loadFromDatabase(symbol, Timeframe.H1, startTime, endTime);
        if (h1Candles.isEmpty()) {
            log.warn("No H1 data in database, generating sample data");
            h1Candles = dataLoader.generateSampleData(symbol, Timeframe.H1, 1000, 50000, 1);
        }

        List<Candle> h4Candles = dataLoader.loadFromDatabase(symbol, Timeframe.H4, startTime, endTime);
        if (h4Candles.isEmpty()) {
            log.warn("No H4 data in database, generating sample data");
            h4Candles = dataLoader.generateSampleData(symbol, Timeframe.H4, 250, 50000, 1);
        }

        data.put(Timeframe.M1, m1Candles);
        data.put(Timeframe.H1, h1Candles);
        data.put(Timeframe.H4, h4Candles);

        return data;
    }

    /**
     * Check for entry signal at current candle
     */
    private void checkForEntrySignal(BacktestState state, Map<Timeframe, List<Candle>> candleData,
                                      int currentIndex, BigDecimal currentPrice, Instant currentTime) {
        // Get recent candles for analysis
        List<Candle> recentH4 = getRecentCandles(candleData.get(Timeframe.H4), currentTime, 100);
        List<Candle> recentH1 = getRecentCandles(candleData.get(Timeframe.H1), currentTime, 100);
        List<Candle> recentM1 = candleData.get(Timeframe.M1).subList(
            Math.max(0, currentIndex - 100), currentIndex + 1
        );

        // Analyze H4 trend
        TrendAnalysis h4Analysis = marketScannerService.analyzeTrend(recentH4, state.symbol, Timeframe.H4);
        if (h4Analysis == null || !h4Analysis.isTradeable()) {
            return;
        }

        // Analyze H1 trend
        TrendAnalysis h1Analysis = marketScannerService.analyzeTrend(recentH1, state.symbol, Timeframe.H1);
        if (h1Analysis == null || !h1Analysis.isTradeable()) {
            return;
        }

        // Check for entry signal on 1m
        EntrySignal signal = signalDetectorService.detectEntrySignal(state.symbol, h4Analysis, h1Analysis);
        if (signal == null || !signal.isValid()) {
            return;
        }

        // Check risk management
        if (!riskManagerService.canOpenNewTrade(state.balance)) {
            return;
        }

        // Calculate position size
        BigDecimal positionSize = riskManagerService.calculatePositionSize(
            state.balance, signal, signal.getAtr()
        );

        // Open position
        openPosition(state, signal, positionSize, currentPrice, currentTime);
    }

    /**
     * Open a new position
     */
    private void openPosition(BacktestState state, EntrySignal signal, BigDecimal positionSize,
                               BigDecimal entryPrice, Instant entryTime) {
        // Calculate slippage
        BigDecimal slippagePercent = MIN_SLIPPAGE.add(
            MAX_SLIPPAGE.subtract(MIN_SLIPPAGE).multiply(BigDecimal.valueOf(Math.random()))
        );

        BigDecimal actualEntryPrice;
        if (signal.getDirection() == TradeDirection.LONG) {
            // Buying - price increases due to slippage
            actualEntryPrice = entryPrice.multiply(BigDecimal.ONE.add(slippagePercent));
        } else {
            // Selling - price decreases due to slippage
            actualEntryPrice = entryPrice.multiply(BigDecimal.ONE.subtract(slippagePercent));
        }

        // Calculate fees
        BigDecimal notionalValue = positionSize.multiply(actualEntryPrice);
        BigDecimal entryFee = notionalValue.multiply(TAKER_FEE);
        BigDecimal entrySlippage = actualEntryPrice.subtract(entryPrice).abs().multiply(positionSize);

        // Create position
        state.currentPosition = BacktestPosition.builder()
            .symbol(state.symbol)
            .direction(signal.getDirection())
            .entryTime(entryTime)
            .entryPrice(actualEntryPrice)
            .positionSize(positionSize)
            .positionSizeUsdt(notionalValue)
            .leverage(strategyProperties.getRisk().getDefaultLeverage())
            .stopLoss(signal.getStopLoss())
            .currentStopLoss(signal.getStopLoss())
            .tp1(signal.getTakeProfit1())
            .tp2(signal.getTakeProfit2())
            .tp3(signal.getTakeProfit3())
            .tp1Hit(false)
            .tp2Hit(false)
            .remainingPositionPercent(BigDecimal.valueOf(100))
            .entryFee(entryFee)
            .entrySlippage(entrySlippage)
            .build();

        // Deduct fees from balance
        state.balance = state.balance.subtract(entryFee);

        log.info("BACKTEST: Opened {} position at ${} (size: ${}, SL: ${}, TP1: ${})",
            signal.getDirection(), actualEntryPrice, positionSize, signal.getStopLoss(), signal.getTakeProfit1());
    }

    /**
     * Update existing position (check TP/SL)
     */
    private void updatePosition(BacktestState state, BigDecimal currentPrice, Instant currentTime,
                                 Map<Timeframe, List<Candle>> candleData, int currentIndex) {
        BacktestPosition pos = state.currentPosition;

        // Update highest/lowest price for trailing stop
        pos.updateHighestPrice(currentPrice);

        // Check stop loss
        if (pos.isStopLossHit(currentPrice)) {
            closePosition(state, currentPrice, currentTime, "STOP_LOSS");
            return;
        }

        // Check TP1
        if (pos.isTp1Hit(currentPrice)) {
            executePartialExit(state, currentPrice, currentTime, 50, "TP1_HIT");
            pos.setTp1Hit(true);
            pos.moveStopLossToBreakeven();
            return;
        }

        // Check TP2
        if (pos.isTp2Hit(currentPrice)) {
            executePartialExit(state, currentPrice, currentTime, 60, "TP2_HIT"); // 60% of remaining = 30% of original
            pos.setTp2Hit(true);
            return;
        }

        // Check TP3 (trailing stop)
        if (pos.isTp3Hit(currentPrice)) {
            closePosition(state, currentPrice, currentTime, "TP3_HIT");
            return;
        }

        // Update trailing stop after TP2
        if (pos.isTp2Hit()) {
            BigDecimal trailingPercent = strategyProperties.getExit().getTakeProfit().getTp3().getTrailingStopPercent();
            pos.updateTrailingStop(trailingPercent);
        }

        // Time-based exits
        long holdingSeconds = Duration.between(pos.getEntryTime(), currentTime).getSeconds();
        int maxHoldSeconds = strategyProperties.getExit().getTimeBased().getMaxHoldMinutes() * 60;

        if (holdingSeconds >= maxHoldSeconds) {
            closePosition(state, currentPrice, currentTime, "MAX_HOLD_TIME");
        }
    }

    /**
     * Execute partial exit
     */
    private void executePartialExit(BacktestState state, BigDecimal exitPrice, Instant exitTime,
                                     int exitPercent, String reason) {
        BacktestPosition pos = state.currentPosition;

        // Calculate slippage for exit
        BigDecimal slippagePercent = MIN_SLIPPAGE.add(
            MAX_SLIPPAGE.subtract(MIN_SLIPPAGE).multiply(BigDecimal.valueOf(Math.random()))
        );

        BigDecimal actualExitPrice;
        if (pos.getDirection() == TradeDirection.LONG) {
            actualExitPrice = exitPrice.multiply(BigDecimal.ONE.subtract(slippagePercent));
        } else {
            actualExitPrice = exitPrice.multiply(BigDecimal.ONE.add(slippagePercent));
        }

        // Calculate PnL for this partial exit
        BigDecimal exitSizePercent = pos.getRemainingPositionPercent()
            .multiply(BigDecimal.valueOf(exitPercent))
            .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        BigDecimal exitPnl = pos.calculateUnrealizedPnl(actualExitPrice)
            .multiply(BigDecimal.valueOf(exitPercent))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Calculate fees
        BigDecimal exitNotional = pos.getPositionSizeUsdt()
            .multiply(exitSizePercent)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal exitFee = exitNotional.multiply(TAKER_FEE);

        // Update balance
        state.balance = state.balance.add(exitPnl).subtract(exitFee);

        // Update position
        pos.executePartialExit(exitPercent);

        log.debug("BACKTEST: Partial exit {}% at ${} (PnL: ${}, Reason: {})",
            exitPercent, actualExitPrice, exitPnl, reason);
    }

    /**
     * Close position completely
     */
    private void closePosition(BacktestState state, BigDecimal exitPrice, Instant exitTime, String exitReason) {
        BacktestPosition pos = state.currentPosition;

        // Calculate slippage for exit
        BigDecimal slippagePercent = MIN_SLIPPAGE.add(
            MAX_SLIPPAGE.subtract(MIN_SLIPPAGE).multiply(BigDecimal.valueOf(Math.random()))
        );

        BigDecimal actualExitPrice;
        if (pos.getDirection() == TradeDirection.LONG) {
            actualExitPrice = exitPrice.multiply(BigDecimal.ONE.subtract(slippagePercent));
        } else {
            actualExitPrice = exitPrice.multiply(BigDecimal.ONE.add(slippagePercent));
        }

        // Calculate final PnL
        BigDecimal pnl = pos.calculateUnrealizedPnl(actualExitPrice);

        // Calculate exit fees
        BigDecimal exitNotional = pos.getPositionSizeUsdt()
            .multiply(pos.getRemainingPositionPercent())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal exitFee = exitNotional.multiply(TAKER_FEE);
        BigDecimal exitSlippage = actualExitPrice.subtract(exitPrice).abs()
            .multiply(pos.getPositionSize())
            .multiply(pos.getRemainingPositionPercent())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Calculate net PnL after all costs
        BigDecimal totalFees = pos.getEntryFee().add(exitFee);
        BigDecimal totalSlippage = pos.getEntrySlippage().add(exitSlippage);
        BigDecimal netPnl = pnl.subtract(totalFees).subtract(totalSlippage);

        // Update balance
        state.balance = state.balance.add(pnl).subtract(exitFee);

        // Create trade record
        BacktestTrade trade = BacktestTrade.builder()
            .tradeId("BT-" + System.currentTimeMillis())
            .symbol(pos.getSymbol())
            .direction(pos.getDirection())
            .entryTime(pos.getEntryTime())
            .exitTime(exitTime)
            .entryPrice(pos.getEntryPrice())
            .exitPrice(actualExitPrice)
            .positionSize(pos.getPositionSize())
            .leverage(pos.getLeverage())
            .stopLoss(pos.getStopLoss())
            .takeProfitLevels(List.of(pos.getTp1(), pos.getTp2(), pos.getTp3()))
            .profitLossUsdt(pnl)
            .fees(totalFees)
            .slippage(totalSlippage)
            .netProfitLoss(netPnl)
            .exitReason(exitReason)
            .build();

        trade.calculateHoldingTime();
        trade.calculateProfitLossPercent();

        state.completedTrades.add(trade);
        state.currentPosition = null;
        state.lastTradeExitTime = exitTime;

        log.info("BACKTEST: Closed {} position at ${} (PnL: ${}, Net: ${}, Reason: {})",
            trade.getDirection(), actualExitPrice, pnl, netPnl, exitReason);
    }

    /**
     * Check if we're on trade cooldown
     */
    private boolean isOnTradeCooldown(BacktestState state, Instant currentTime) {
        if (state.lastTradeExitTime == null) {
            return false;
        }

        // 1-minute cooldown between trades
        long secondsSinceLastTrade = Duration.between(state.lastTradeExitTime, currentTime).getSeconds();
        return secondsSinceLastTrade < 60;
    }

    /**
     * Get recent candles up to a specific time
     */
    private List<Candle> getRecentCandles(List<Candle> allCandles, Instant upToTime, int count) {
        List<Candle> recent = new ArrayList<>();
        for (int i = allCandles.size() - 1; i >= 0 && recent.size() < count; i--) {
            if (allCandles.get(i).getCloseTime().isBefore(upToTime) ||
                allCandles.get(i).getCloseTime().equals(upToTime)) {
                recent.add(0, allCandles.get(i));
            }
        }
        return recent;
    }

    /**
     * Generate final backtest results
     */
    private BacktestResult generateResults(BacktestState state, String symbol,
                                            Instant startTime, Instant endTime) {
        BacktestResult result = BacktestResult.builder()
            .symbol(symbol)
            .startTime(startTime)
            .endTime(endTime)
            .initialBalance(state.initialBalance)
            .finalBalance(state.balance)
            .trades(state.completedTrades)
            .maxDrawdown(state.maxDrawdown)
            .maxDrawdownUsd(state.maxDrawdownUsd)
            .maxDrawdownDate(state.maxDrawdownDate)
            .build();

        result.calculateMetrics();

        log.info("Backtest complete: {} trades, Win rate: {}%, Profit factor: {}, Final balance: ${}",
            result.getTotalTrades(), result.getWinRate(), result.getProfitFactor(), state.balance);

        return result;
    }

    /**
     * Create empty result for failed backtest
     */
    private BacktestResult createEmptyResult(String symbol, Instant startTime, Instant endTime,
                                              BigDecimal initialBalance) {
        return BacktestResult.builder()
            .symbol(symbol)
            .startTime(startTime)
            .endTime(endTime)
            .initialBalance(initialBalance)
            .finalBalance(initialBalance)
            .trades(new ArrayList<>())
            .build();
    }

    /**
     * Internal backtest state
     */
    private static class BacktestState {
        String symbol;
        BigDecimal balance;
        BigDecimal initialBalance;
        BigDecimal equity;
        BigDecimal peakEquity;
        BigDecimal maxDrawdown;
        BigDecimal maxDrawdownUsd;
        Instant maxDrawdownDate;
        BacktestPosition currentPosition;
        List<BacktestTrade> completedTrades = new ArrayList<>();
        Instant lastTradeExitTime;
    }
}
