package com.bot.gift.x1000.martiangletradingbot.exchange.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Order request for exchange
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    private String symbol;
    private String side; // BUY or SELL
    private String type; // MARKET, LIMIT, etc.
    private BigDecimal quantity;
    private BigDecimal price; // For LIMIT orders
    private String timeInForce; // GTC, IOC, FOK
    private BigDecimal stopPrice; // For STOP orders
    private String positionSide; // BOTH, LONG, SHORT (for hedge mode)
}
