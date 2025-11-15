package com.bot.gift.x1000.martiangletradingbot.model.enums;

/**
 * Reason why a trade was exited
 */
public enum ExitReason {
    TP1_HIT,              // Take Profit Level 1 hit
    TP2_HIT,              // Take Profit Level 2 hit
    TP3_HIT,              // Take Profit Level 3 hit
    TRAILING_STOP,        // Trailing stop hit
    STOP_LOSS,            // Stop loss hit
    TIME_BASED_EXIT,      // Max holding time exceeded
    EMERGENCY_EXIT,       // Emergency condition triggered
    EMA_BREAK,            // EMA21 broken
    MANUAL_EXIT,          // Manually closed
    DAILY_LIMIT_REACHED,  // Daily loss/trade limit reached
    TREND_REVERSAL        // Trend reversed
}
