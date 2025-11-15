package com.bot.gift.x1000.martiangletradingbot.model.enums;

/**
 * Status of a trade
 */
public enum TradeStatus {
    PENDING,      // Trade signal detected, waiting to enter
    OPEN,         // Trade is active
    PARTIALLY_CLOSED,  // Some TP levels hit
    CLOSED,       // Trade fully closed
    CANCELLED     // Trade cancelled before entry
}
