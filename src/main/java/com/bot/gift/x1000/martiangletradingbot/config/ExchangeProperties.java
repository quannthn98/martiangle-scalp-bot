package com.bot.gift.x1000.martiangletradingbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Exchange configuration properties
 * Binds to 'exchange.binance' section in application.yml
 */
@Configuration
@ConfigurationProperties(prefix = "exchange.binance")
@Data
public class ExchangeProperties {

    private String apiKey;
    private String secretKey;
    private boolean testnet;
    private String baseUrl;
    private String wsUrl;
    private int requestTimeout;
    private int maxRetries;
    private int retryDelayMs;
}
