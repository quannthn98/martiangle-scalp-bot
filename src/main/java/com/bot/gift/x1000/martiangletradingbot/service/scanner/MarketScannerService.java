package com.bot.gift.x1000.martiangletradingbot.service.scanner;

import com.bot.gift.x1000.martiangletradingbot.config.StrategyProperties;
import com.bot.gift.x1000.martiangletradingbot.indicator.*;
import com.bot.gift.x1000.martiangletradingbot.model.dto.TrendAnalysis;
import com.bot.gift.x1000.martiangletradingbot.model.entity.Candle;
import com.bot.gift.x1000.martiangletradingbot.model.enums.Timeframe;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TrendDirection;
import com.bot.gift.x1000.martiangletradingbot.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Market Scanner Service
 * Scans markets for trending pairs on H4 and H1 timeframes
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MarketScannerService {

    private final CandleRepository candleRepository;
    private final EMAIndicator emaIndicator;
    private final RSIIndicator rsiIndicator;
    private final ADXIndicator adxIndicator;
    private final VolumeIndicator volumeIndicator;
    private final BollingerBandsIndicator bollingerBandsIndicator;
    private final StrategyProperties strategyProperties;

    /**
     * Scan H4 timeframe for trending pairs
     *
     * @param symbols List of symbols to scan
     * @return List of trend analyses for tradeable pairs
     */
    public List<TrendAnalysis> scanH4Trends(List<String> symbols) {
        log.info("Scanning H4 trends for {} symbols", symbols.size());

        List<TrendAnalysis> results = new ArrayList<>();

        for (String symbol : symbols) {
            try {
                TrendAnalysis analysis = analyzeH4Trend(symbol);
                if (analysis != null) {
                    results.add(analysis);
                }
            } catch (Exception e) {
                log.error("Error analyzing H4 trend for {}: {}", symbol, e.getMessage());
            }
        }

        log.info("Found {} tradeable pairs on H4", results.stream().filter(TrendAnalysis::isTradeable).count());
        return results;
    }

    /**
     * Analyze H4 trend for a specific symbol
     */
    public TrendAnalysis analyzeH4Trend(String symbol) {
        // Fetch 200 H4 candles
        List<Candle> candles = candleRepository.findLatestCandles(
            symbol,
            Timeframe.FOUR_HOURS,
            PageRequest.of(0, 200)
        );

        if (candles.size() < 200) {
            log.warn("Insufficient H4 candles for {}: {}", symbol, candles.size());
            return null;
        }

        // Reverse to oldest first
        candles = new ArrayList<>(candles);
        java.util.Collections.reverse(candles);

        return analyzeTrend(symbol, candles, Timeframe.FOUR_HOURS, true);
    }

    /**
     * Analyze H1 trend for trend confirmation
     */
    public TrendAnalysis analyzeH1Trend(String symbol) {
        List<Candle> candles = candleRepository.findLatestCandles(
            symbol,
            Timeframe.ONE_HOUR,
            PageRequest.of(0, 200)
        );

        if (candles.size() < 200) {
            log.warn("Insufficient H1 candles for {}: {}", symbol, candles.size());
            return null;
        }

        candles = new ArrayList<>(candles);
        java.util.Collections.reverse(candles);

        return analyzeTrend(symbol, candles, Timeframe.ONE_HOUR, false);
    }

    /**
     * Core trend analysis logic
     */
    private TrendAnalysis analyzeTrend(String symbol, List<Candle> candles,
                                      Timeframe timeframe, boolean isH4) {
        TrendAnalysis analysis = TrendAnalysis.builder()
            .symbol(symbol)
            .timeframe(timeframe)
            .isTradeable(false)
            .build();

        // Extract closing prices
        List<BigDecimal> closePrices = candles.stream()
            .map(Candle::getClose)
            .collect(Collectors.toList());

        // Calculate indicators
        int emaFastPeriod = strategyProperties.getIndicators().getEma().getFast();
        int emaSlowPeriod = strategyProperties.getIndicators().getEma().getSlow();
        int adxPeriod = strategyProperties.getIndicators().getAdx().getPeriod();
        int bbPeriod = strategyProperties.getIndicators().getBollinger().getPeriod();
        int bbStdDev = strategyProperties.getIndicators().getBollinger().getStdDev();

        List<BigDecimal> ema34Values = emaIndicator.calculate(closePrices, emaFastPeriod);
        List<BigDecimal> ema89Values = emaIndicator.calculate(closePrices, emaSlowPeriod);
        ADXIndicator.ADXResult adxResult = adxIndicator.calculate(candles, adxPeriod);
        BollingerBandsIndicator.BBResult bbResult = bollingerBandsIndicator.calculate(closePrices, bbPeriod, bbStdDev);
        List<BigDecimal> volumeMA = volumeIndicator.calculateVolumeMA(candles, 20);

        // Get latest values
        BigDecimal currentPrice = closePrices.get(closePrices.size() - 1);
        BigDecimal ema34 = ema34Values.get(ema34Values.size() - 1);
        BigDecimal ema89 = ema89Values.get(ema89Values.size() - 1);
        BigDecimal adx = adxResult.getAdx().get(adxResult.getAdx().size() - 1);
        BigDecimal plusDI = adxResult.getPlusDI().get(adxResult.getPlusDI().size() - 1);
        BigDecimal minusDI = adxResult.getMinusDI().get(adxResult.getMinusDI().size() - 1);
        BigDecimal bbBandwidth = bbResult.getBandwidth().get(bbResult.getBandwidth().size() - 1);
        BigDecimal currentVolume = candles.get(candles.size() - 1).getVolume();
        BigDecimal volumeMa = volumeMA.get(volumeMA.size() - 1);

        // Set basic values
        analysis.setCurrentPrice(currentPrice);
        analysis.setEma34(ema34);
        analysis.setEma89(ema89);
        analysis.setAdx(adx);
        analysis.setPlusDI(plusDI);
        analysis.setMinusDI(minusDI);
        analysis.setBbBandwidth(bbBandwidth);
        analysis.setVolumeRatio(volumeIndicator.calculateVolumeRatio(currentVolume, volumeMa));

        // Determine trend direction and check EMA alignment
        boolean bullishEmaAlignment = currentPrice.compareTo(ema34) > 0 && ema34.compareTo(ema89) > 0;
        boolean bearishEmaAlignment = currentPrice.compareTo(ema34) < 0 && ema34.compareTo(ema89) < 0;

        analysis.setEmaAligned(bullishEmaAlignment || bearishEmaAlignment);

        if (bullishEmaAlignment) {
            analysis.setTrendDirection(TrendDirection.BULLISH);
        } else if (bearishEmaAlignment) {
            analysis.setTrendDirection(TrendDirection.BEARISH);
        } else {
            analysis.setTrendDirection(TrendDirection.SIDEWAYS);
            analysis.setRejectionReason("EMA not aligned");
            return analysis;
        }

        // Check EMA separation
        BigDecimal emaSeparation = ema34.subtract(ema89).abs()
            .divide(ema89, 8, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        analysis.setEmaSeparationPercent(emaSeparation);

        BigDecimal minSeparation = strategyProperties.getMarketSelection().getMinEmaSeparationPercent();
        if (emaSeparation.compareTo(minSeparation) < 0) {
            analysis.setRejectionReason("EMA separation too small: " + emaSeparation + "%");
            return analysis;
        }

        // Check ADX strength
        BigDecimal adxThreshold = isH4
            ? BigDecimal.valueOf(strategyProperties.getIndicators().getAdx().getH4Threshold())
            : BigDecimal.valueOf(strategyProperties.getIndicators().getAdx().getH1Threshold());

        analysis.setStrongTrend(adx.compareTo(adxThreshold) > 0);

        if (!analysis.isStrongTrend()) {
            analysis.setRejectionReason("ADX too weak: " + adx);
            return analysis;
        }

        // Check price distance from EMA34
        BigDecimal priceDistance = currentPrice.subtract(ema34).abs()
            .divide(ema34, 8, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        analysis.setPriceDistanceFromEma34Percent(priceDistance);

        BigDecimal maxDistance = strategyProperties.getMarketSelection().getMaxDistanceFromEma34Percent();
        if (priceDistance.compareTo(maxDistance) > 0) {
            analysis.setRejectionReason("Price too far from EMA34: " + priceDistance + "%");
            return analysis;
        }

        // Check Bollinger Band squeeze
        BigDecimal squeezeThreshold = strategyProperties.getIndicators().getBollinger().getSqueezeThreshold();
        analysis.setInSqueeze(bollingerBandsIndicator.isInSqueeze(bbBandwidth, squeezeThreshold));

        if (analysis.isInSqueeze()) {
            analysis.setRejectionReason("In Bollinger Band squeeze - low volatility");
            return analysis;
        }

        // All checks passed - tradeable!
        analysis.setTradeable(true);
        log.info("Found tradeable {} trend on {} for {}", analysis.getTrendDirection(), timeframe, symbol);

        return analysis;
    }

    /**
     * Filter symbols by tier priority
     */
    public List<String> getAllTradingPairs() {
        List<String> allPairs = new ArrayList<>();
        allPairs.addAll(strategyProperties.getMarketSelection().getTier1Pairs());
        allPairs.addAll(strategyProperties.getMarketSelection().getTier2Pairs());
        return allPairs;
    }

    /**
     * Get tier 1 pairs (highest priority)
     */
    public List<String> getTier1Pairs() {
        return new ArrayList<>(strategyProperties.getMarketSelection().getTier1Pairs());
    }
}
