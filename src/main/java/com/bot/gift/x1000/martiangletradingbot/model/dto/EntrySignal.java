package com.bot.gift.x1000.martiangletradingbot.model.dto;

import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entry signal for a trading opportunity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntrySignal {

    private String symbol;
    private TradeDirection direction;
    private Instant signalTime;

    // Entry information
    private BigDecimal entryPrice;
    private BigDecimal stopLoss;
    private BigDecimal takeProfit1;
    private BigDecimal takeProfit2;
    private BigDecimal takeProfit3;

    // Technical indicators at signal
    private BigDecimal ema21;
    private BigDecimal rsi;
    private BigDecimal atr;
    private BigDecimal volumeRatio;

    // Signal strength
    private int signalStrength; // 0-100
    private boolean isValid;
    private String invalidReason;

    // DCA information
    private int maxDcaLevels;
    private BigDecimal dcaSpacingPercent;

    // Risk information
    private BigDecimal riskRewardRatio;
    private BigDecimal expectedProfit;
}
