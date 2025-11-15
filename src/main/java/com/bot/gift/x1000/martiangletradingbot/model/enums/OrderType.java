package com.bot.gift.x1000.martiangletradingbot.model.enums;

/**
 * Type of order
 */
public enum OrderType {
    MARKET,       // Market order (immediate execution)
    LIMIT,        // Limit order
    STOP_MARKET,  // Stop loss market order
    TAKE_PROFIT   // Take profit order
}
