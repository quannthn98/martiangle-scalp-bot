package com.bot.gift.x1000.martiangletradingbot.exchange.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Order response from exchange
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private String orderId;
    private String symbol;
    private String status; // NEW, FILLED, PARTIALLY_FILLED, CANCELED, etc.
    private String side;
    private String type;
    private BigDecimal price;
    private BigDecimal executedQty;
    private BigDecimal avgPrice;
    private Instant transactTime;
    private String clientOrderId;
}
