package com.bot.gift.x1000.martiangletradingbot.service.signal;

import com.bot.gift.x1000.martiangletradingbot.config.StrategyProperties;
import com.bot.gift.x1000.martiangletradingbot.indicator.*;
import com.bot.gift.x1000.martiangletradingbot.model.dto.EntrySignal;
import com.bot.gift.x1000.martiangletradingbot.model.dto.TrendAnalysis;
import com.bot.gift.x1000.martiangletradingbot.model.entity.Candle;
import com.bot.gift.x1000.martiangletradingbot.model.enums.Timeframe;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeDirection;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TrendDirection;
import com.bot.gift.x1000.martiangletradingbot.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Signal Detector Service
 * Detects entry signals on 1-minute timeframe
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SignalDetectorService {

    private final CandleRepository candleRepository;
    private final EMAIndicator emaIndicator;
    private final RSIIndicator rsiIndicator;
    private final ATRIndicator atrIndicator;
    private final VolumeIndicator volumeIndicator;
    private final StrategyProperties strategyProperties;

    /**
     * Detect entry signal for a symbol
     *
     * @param symbol Symbol to analyze
     * @param h4Trend H4 trend analysis (must be provided)
     * @param h1Trend H1 trend analysis (must be provided)
     * @return Entry signal or null if no valid signal
     */
    public EntrySignal detectEntrySignal(String symbol, TrendAnalysis h4Trend, TrendAnalysis h1Trend) {
        // Validate trend analyses
        if (h4Trend == null || h1Trend == null) {
            return null;
        }

        if (!h4Trend.isTradeable() || !h1Trend.isTradeable()) {
            log.debug("Trends not tradeable for {}", symbol);
            return null;
        }

        // Trends must align
        if (h4Trend.getTrendDirection() != h1Trend.getTrendDirection()) {
            log.debug("H4 and H1 trends don't align for {}", symbol);
            return null;
        }

        // Fetch 1m candles
        List<Candle> candles = candleRepository.findLatestCandles(
            symbol,
            Timeframe.ONE_MINUTE,
            PageRequest.of(0, 500)
        );

        if (candles.size() < 100) {
            log.warn("Insufficient 1m candles for {}: {}", symbol, candles.size());
            return null;
        }

        // Reverse to oldest first
        candles = new ArrayList<>(candles);
        java.util.Collections.reverse(candles);

        TrendDirection trend = h4Trend.getTrendDirection();

        if (trend == TrendDirection.BULLISH) {
            return detectLongSignal(symbol, candles);
        } else if (trend == TrendDirection.BEARISH) {
            return detectShortSignal(symbol, candles);
        }

        return null;
    }

    /**
     * Detect LONG entry signal
     */
    private EntrySignal detectLongSignal(String symbol, List<Candle> candles) {
        EntrySignal signal = EntrySignal.builder()
            .symbol(symbol)
            .direction(TradeDirection.LONG)
            .signalTime(Instant.now())
            .isValid(false)
            .build();

        // Extract data
        List<BigDecimal> closePrices = candles.stream()
            .map(Candle::getClose)
            .collect(Collectors.toList());

        Candle currentCandle = candles.get(candles.size() - 1);
        Candle previousCandle = candles.get(candles.size() - 2);

        // Calculate indicators
        int ema21Period = strategyProperties.getIndicators().getEma().getEntry();
        int rsiPeriod = strategyProperties.getIndicators().getRsi().getPeriod();
        int atrPeriod = strategyProperties.getIndicators().getAtr().getPeriod();

        List<BigDecimal> ema21Values = emaIndicator.calculate(closePrices, ema21Period);
        List<BigDecimal> rsiValues = rsiIndicator.calculate(closePrices, rsiPeriod);
        List<BigDecimal> atrValues = atrIndicator.calculate(candles, atrPeriod);
        List<BigDecimal> volumeMA = volumeIndicator.calculateVolumeMA(candles, 20);

        // Get latest values
        BigDecimal currentPrice = currentCandle.getClose();
        BigDecimal ema21 = ema21Values.get(ema21Values.size() - 1);
        BigDecimal rsi = rsiValues.get(rsiValues.size() - 1);
        BigDecimal atr = atrValues.get(atrValues.size() - 1);
        BigDecimal volumeMa = volumeMA.get(volumeMA.size() - 1);

        signal.setEntryPrice(currentPrice);
        signal.setEma21(ema21);
        signal.setRsi(rsi);
        signal.setAtr(atr);
        signal.setVolumeRatio(volumeIndicator.calculateVolumeRatio(currentCandle.getVolume(), volumeMa));

        // Check LONG entry conditions

        // 1. Current candle is GREEN
        if (!currentCandle.isGreen()) {
            signal.setInvalidReason("Current candle is not green");
            return signal;
        }

        // 2. Price > EMA21
        if (currentPrice.compareTo(ema21) <= 0) {
            signal.setInvalidReason("Price not above EMA21");
            return signal;
        }

        // 3. RSI in valid range (50-80)
        int rsiMin = strategyProperties.getEntry().getConditions().getLongCondition().getRsiMin();
        int rsiMax = strategyProperties.getEntry().getConditions().getLongCondition().getRsiMax();

        if (!rsiIndicator.isInRange(rsi, BigDecimal.valueOf(rsiMin), BigDecimal.valueOf(rsiMax))) {
            signal.setInvalidReason("RSI out of range: " + rsi);
            return signal;
        }

        // 4. Check for RSI bearish divergence (avoid if present)
        if (rsiIndicator.detectBearishDivergence(closePrices, rsiValues, 10)) {
            signal.setInvalidReason("Bearish RSI divergence detected");
            return signal;
        }

        // 5. ATR rising but not spiking
        if (!atrIndicator.isAtrRising(atrValues, 5)) {
            signal.setInvalidReason("ATR not rising");
            return signal;
        }

        BigDecimal atrSpikeThreshold = strategyProperties.getIndicators().getAtr().getSpikeThreshold();
        if (atrIndicator.isAtrSpiking(atr, atrValues, 20, atrSpikeThreshold)) {
            signal.setInvalidReason("ATR spiking - too volatile");
            return signal;
        }

        // 6. Volume > 50% of MA
        BigDecimal minVolumeRatio = BigDecimal.valueOf(0.5);
        if (!volumeIndicator.meetsMinimumVolume(signal.getVolumeRatio(), minVolumeRatio)) {
            signal.setInvalidReason("Volume too low");
            return signal;
        }

        // 7. Entry trigger: Wait for first RED candle after conditions met
        // For now, if current is green and all conditions met, signal is valid
        // In practice, you'd wait for the next red candle to close

        // Calculate stop loss and take profits
        calculateExitLevels(signal, atr);

        signal.setIsValid(true);
        signal.setSignalStrength(calculateSignalStrength(signal));
        signal.setMaxDcaLevels(strategyProperties.getEntry().getDca().getMaxEntries());
        signal.setDcaSpacingPercent(strategyProperties.getEntry().getDca().getMinSpacingPercent());

        log.info("LONG signal detected for {} at price {}, RSI: {}, Signal strength: {}",
            symbol, currentPrice, rsi, signal.getSignalStrength());

        return signal;
    }

    /**
     * Detect SHORT entry signal
     */
    private EntrySignal detectShortSignal(String symbol, List<Candle> candles) {
        EntrySignal signal = EntrySignal.builder()
            .symbol(symbol)
            .direction(TradeDirection.SHORT)
            .signalTime(Instant.now())
            .isValid(false)
            .build();

        // Extract data
        List<BigDecimal> closePrices = candles.stream()
            .map(Candle::getClose)
            .collect(Collectors.toList());

        Candle currentCandle = candles.get(candles.size() - 1);

        // Calculate indicators
        int ema21Period = strategyProperties.getIndicators().getEma().getEntry();
        int rsiPeriod = strategyProperties.getIndicators().getRsi().getPeriod();
        int atrPeriod = strategyProperties.getIndicators().getAtr().getPeriod();

        List<BigDecimal> ema21Values = emaIndicator.calculate(closePrices, ema21Period);
        List<BigDecimal> rsiValues = rsiIndicator.calculate(closePrices, rsiPeriod);
        List<BigDecimal> atrValues = atrIndicator.calculate(candles, atrPeriod);
        List<BigDecimal> volumeMA = volumeIndicator.calculateVolumeMA(candles, 20);

        // Get latest values
        BigDecimal currentPrice = currentCandle.getClose();
        BigDecimal ema21 = ema21Values.get(ema21Values.size() - 1);
        BigDecimal rsi = rsiValues.get(rsiValues.size() - 1);
        BigDecimal atr = atrValues.get(atrValues.size() - 1);
        BigDecimal volumeMa = volumeMA.get(volumeMA.size() - 1);

        signal.setEntryPrice(currentPrice);
        signal.setEma21(ema21);
        signal.setRsi(rsi);
        signal.setAtr(atr);
        signal.setVolumeRatio(volumeIndicator.calculateVolumeRatio(currentCandle.getVolume(), volumeMa));

        // Check SHORT entry conditions

        // 1. Current candle is RED
        if (!currentCandle.isRed()) {
            signal.setInvalidReason("Current candle is not red");
            return signal;
        }

        // 2. Price < EMA21
        if (currentPrice.compareTo(ema21) >= 0) {
            signal.setInvalidReason("Price not below EMA21");
            return signal;
        }

        // 3. RSI in valid range (20-50)
        int rsiMin = strategyProperties.getEntry().getConditions().getShortCondition().getRsiMin();
        int rsiMax = strategyProperties.getEntry().getConditions().getShortCondition().getRsiMax();

        if (!rsiIndicator.isInRange(rsi, BigDecimal.valueOf(rsiMin), BigDecimal.valueOf(rsiMax))) {
            signal.setInvalidReason("RSI out of range: " + rsi);
            return signal;
        }

        // 4. Check for RSI bullish divergence (avoid if present)
        if (rsiIndicator.detectBullishDivergence(closePrices, rsiValues, 10)) {
            signal.setInvalidReason("Bullish RSI divergence detected");
            return signal;
        }

        // 5. ATR rising but not spiking
        if (!atrIndicator.isAtrRising(atrValues, 5)) {
            signal.setInvalidReason("ATR not rising");
            return signal;
        }

        BigDecimal atrSpikeThreshold = strategyProperties.getIndicators().getAtr().getSpikeThreshold();
        if (atrIndicator.isAtrSpiking(atr, atrValues, 20, atrSpikeThreshold)) {
            signal.setInvalidReason("ATR spiking - too volatile");
            return signal;
        }

        // 6. Volume > 50% of MA
        BigDecimal minVolumeRatio = BigDecimal.valueOf(0.5);
        if (!volumeIndicator.meetsMinimumVolume(signal.getVolumeRatio(), minVolumeRatio)) {
            signal.setInvalidReason("Volume too low");
            return signal;
        }

        // Calculate stop loss and take profits
        calculateExitLevels(signal, atr);

        signal.setIsValid(true);
        signal.setSignalStrength(calculateSignalStrength(signal));
        signal.setMaxDcaLevels(strategyProperties.getEntry().getDca().getMaxEntries());
        signal.setDcaSpacingPercent(strategyProperties.getEntry().getDca().getMinSpacingPercent());

        log.info("SHORT signal detected for {} at price {}, RSI: {}, Signal strength: {}",
            symbol, currentPrice, rsi, signal.getSignalStrength());

        return signal;
    }

    /**
     * Calculate stop loss and take profit levels
     */
    private void calculateExitLevels(EntrySignal signal, BigDecimal atr) {
        BigDecimal entryPrice = signal.getEntryPrice();
        BigDecimal stopLossPercent = strategyProperties.getExit().getStopLoss().getInitialPercent();

        // Stop loss
        if (signal.getDirection() == TradeDirection.LONG) {
            signal.setStopLoss(entryPrice.multiply(
                BigDecimal.ONE.subtract(stopLossPercent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            ));
        } else {
            signal.setStopLoss(entryPrice.multiply(
                BigDecimal.ONE.add(stopLossPercent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            ));
        }

        // Take profits
        BigDecimal tp1Percent = strategyProperties.getExit().getTakeProfit().getTp1().getProfitTargetPercent();
        BigDecimal tp2Percent = strategyProperties.getExit().getTakeProfit().getTp2().getProfitTargetPercent();

        if (signal.getDirection() == TradeDirection.LONG) {
            signal.setTakeProfit1(entryPrice.multiply(
                BigDecimal.ONE.add(tp1Percent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            ));
            signal.setTakeProfit2(entryPrice.multiply(
                BigDecimal.ONE.add(tp2Percent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            ));
            signal.setTakeProfit3(entryPrice.multiply(
                BigDecimal.ONE.add(tp2Percent.multiply(BigDecimal.valueOf(1.5)).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            ));
        } else {
            signal.setTakeProfit1(entryPrice.multiply(
                BigDecimal.ONE.subtract(tp1Percent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            ));
            signal.setTakeProfit2(entryPrice.multiply(
                BigDecimal.ONE.subtract(tp2Percent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            ));
            signal.setTakeProfit3(entryPrice.multiply(
                BigDecimal.ONE.subtract(tp2Percent.multiply(BigDecimal.valueOf(1.5)).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP))
            ));
        }

        // Risk/Reward ratio
        BigDecimal risk = entryPrice.subtract(signal.getStopLoss()).abs();
        BigDecimal reward = signal.getTakeProfit2().subtract(entryPrice).abs();
        signal.setRiskRewardRatio(reward.divide(risk, 2, RoundingMode.HALF_UP));
    }

    /**
     * Calculate signal strength (0-100)
     */
    private int calculateSignalStrength(EntrySignal signal) {
        int strength = 50; // Base strength

        // RSI contribution
        BigDecimal rsi = signal.getRsi();
        if (signal.getDirection() == TradeDirection.LONG) {
            if (rsi.compareTo(BigDecimal.valueOf(55)) >= 0 && rsi.compareTo(BigDecimal.valueOf(70)) <= 0) {
                strength += 20; // Ideal RSI range for LONG
            }
        } else {
            if (rsi.compareTo(BigDecimal.valueOf(30)) >= 0 && rsi.compareTo(BigDecimal.valueOf(45)) <= 0) {
                strength += 20; // Ideal RSI range for SHORT
            }
        }

        // Volume contribution
        if (signal.getVolumeRatio().compareTo(BigDecimal.ONE) > 0) {
            strength += 15; // Above average volume
        }

        // Risk/Reward contribution
        if (signal.getRiskRewardRatio().compareTo(BigDecimal.valueOf(1.5)) >= 0) {
            strength += 15; // Good risk/reward
        }

        return Math.min(100, Math.max(0, strength));
    }
}
