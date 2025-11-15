package com.bot.gift.x1000.martiangletradingbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Risk management configuration properties
 * Binds to 'risk' section in application.yml
 */
@Configuration
@ConfigurationProperties(prefix = "risk")
@Data
public class RiskProperties {

    private BigDecimal riskPerTradePercent;
    private BigDecimal riskPerTradeMinPercent;
    private BigDecimal maxDailyLossPercent;
    private int maxDailyTrades;
    private int maxConcurrentTrades;
    private BigDecimal maxTotalExposurePercent;
    private BigDecimal minPositionSizeUsdt;
    private BigDecimal maxPositionSizeUsdt;
    private int defaultLeverage;
    private int maxLeverage;
}
