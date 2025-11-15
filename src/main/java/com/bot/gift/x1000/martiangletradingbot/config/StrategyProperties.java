package com.bot.gift.x1000.martiangletradingbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

/**
 * Strategy configuration properties
 * Binds to 'strategy' section in application.yml
 */
@Configuration
@ConfigurationProperties(prefix = "strategy")
@Data
public class StrategyProperties {

    private String name;
    private String version;
    private Timeframes timeframes;
    private Indicators indicators;
    private MarketSelection marketSelection;
    private Entry entry;
    private Exit exit;

    @Data
    public static class Timeframes {
        private String trendPrimary;
        private String trendSecondary;
        private String execution;
    }

    @Data
    public static class Indicators {
        private Ema ema;
        private Rsi rsi;
        private Atr atr;
        private Adx adx;
        private Volume volume;
        private Bollinger bollinger;
    }

    @Data
    public static class Ema {
        private int fast;
        private int slow;
        private int entry;
    }

    @Data
    public static class Rsi {
        private int period;
        private int lowerBound;
        private int upperBound;
        private int overbought;
        private int extremeOverbought;
        private int oversold;
        private int extremeOversold;
    }

    @Data
    public static class Atr {
        private int period;
        private BigDecimal spikeThreshold;
    }

    @Data
    public static class Adx {
        private int period;
        private int h4Threshold;
        private int h1Threshold;
    }

    @Data
    public static class Volume {
        private int maPeriod;
        private BigDecimal minRatio;
    }

    @Data
    public static class Bollinger {
        private int period;
        private int stdDev;
        private BigDecimal squeezeThreshold;
    }

    @Data
    public static class MarketSelection {
        private List<String> tier1Pairs;
        private List<String> tier2Pairs;
        private Blacklist blacklist;
        private BigDecimal minEmaSeparationPercent;
        private BigDecimal maxDistanceFromEma34Percent;
    }

    @Data
    public static class Blacklist {
        private long min24hVolumeUsd;
        private BigDecimal maxSpreadPercent;
        private BigDecimal maxFundingRate;
        private BigDecimal minFundingRate;
    }

    @Data
    public static class Entry {
        private Dca dca;
        private Conditions conditions;
    }

    @Data
    public static class Dca {
        private int maxEntries;
        private BigDecimal minSpacingPercent;
        private BigDecimal maxSpacingPercent;
        private int rsiThresholdLevel1;
        private int rsiThresholdLevel2;
    }

    @Data
    public static class Conditions {
        private LongCondition longCondition;
        private ShortCondition shortCondition;
    }

    @Data
    public static class LongCondition {
        private int rsiMin;
        private int rsiMax;
        private boolean requirePriceAboveEma21;
        private boolean requireGreenCandle;
    }

    @Data
    public static class ShortCondition {
        private int rsiMin;
        private int rsiMax;
        private boolean requirePriceBelowEma21;
        private boolean requireRedCandle;
    }

    @Data
    public static class Exit {
        private TakeProfit takeProfit;
        private StopLoss stopLoss;
        private TimeBased timeBased;
        private Emergency emergency;
    }

    @Data
    public static class TakeProfit {
        private Tp1 tp1;
        private Tp2 tp2;
        private Tp3 tp3;
    }

    @Data
    public static class Tp1 {
        private int positionPercent;
        private BigDecimal profitTargetPercent;
        private int timeBasedMinutes;
        private BigDecimal minProfitPercent;
    }

    @Data
    public static class Tp2 {
        private int positionPercent;
        private BigDecimal profitTargetPercent;
    }

    @Data
    public static class Tp3 {
        private int positionPercent;
        private BigDecimal trailingStopPercent;
        private int maxHoldMinutes;
    }

    @Data
    public static class StopLoss {
        private BigDecimal initialPercent;
        private BigDecimal swingLowBufferPercent;
        private boolean breakevenAfterTp1;
        private BigDecimal tightenAfter10minPercent;
    }

    @Data
    public static class TimeBased {
        private int forceCloseNoBreakevenMinutes;
        private int partialCloseNoBreakevenMinutes;
        private int maxHoldMinutes;
    }

    @Data
    public static class Emergency {
        private BigDecimal btcDumpPumpPercent;
        private BigDecimal volumeSpikeMultiplier;
        private BigDecimal maxLossPercent;
    }
}
