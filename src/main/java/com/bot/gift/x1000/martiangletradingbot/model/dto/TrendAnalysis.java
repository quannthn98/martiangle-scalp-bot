package com.bot.gift.x1000.martiangletradingbot.model.dto;

import com.bot.gift.x1000.martiangletradingbot.model.enums.Timeframe;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TrendDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Trend analysis result for a symbol
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendAnalysis {

    private String symbol;
    private Timeframe timeframe;
    private TrendDirection trendDirection;
    private boolean isTradeable;

    // EMA values
    private BigDecimal ema21;
    private BigDecimal ema34;
    private BigDecimal ema89;
    private boolean emaAligned;
    private BigDecimal emaSeparationPercent;

    // ADX values
    private BigDecimal adx;
    private BigDecimal plusDI;
    private BigDecimal minusDI;
    private boolean strongTrend;

    // Price position
    private BigDecimal currentPrice;
    private BigDecimal priceDistanceFromEma34Percent;

    // Bollinger Bands
    private BigDecimal bbBandwidth;
    private boolean inSqueeze;

    // Volume
    private BigDecimal volumeRatio;
    private boolean volumeConfirmed;

    // RSI
    private BigDecimal rsi;
    private boolean rsiExtreme;

    private String rejectionReason;
}
