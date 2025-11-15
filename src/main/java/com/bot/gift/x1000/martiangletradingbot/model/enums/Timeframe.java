package com.bot.gift.x1000.martiangletradingbot.model.enums;

import lombok.Getter;

/**
 * Trading timeframes
 */
@Getter
public enum Timeframe {
    ONE_MINUTE("1m", 60),
    FIVE_MINUTES("5m", 300),
    FIFTEEN_MINUTES("15m", 900),
    ONE_HOUR("1h", 3600),
    FOUR_HOURS("4h", 14400),
    ONE_DAY("1d", 86400);

    private final String binanceInterval;
    private final int seconds;

    Timeframe(String binanceInterval, int seconds) {
        this.binanceInterval = binanceInterval;
        this.seconds = seconds;
    }
}
