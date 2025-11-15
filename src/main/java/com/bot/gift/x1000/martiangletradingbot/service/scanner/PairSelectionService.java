package com.bot.gift.x1000.martiangletradingbot.service.scanner;

import com.bot.gift.x1000.martiangletradingbot.config.StrategyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Pair Selection and Blacklist Filtering Service
 * Filters trading pairs based on volume, spread, and funding rate criteria
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PairSelectionService {

    private final StrategyProperties strategyProperties;

    /**
     * Check if a symbol passes blacklist criteria
     *
     * @param symbol Symbol to check
     * @param volume24h 24-hour trading volume in USD
     * @param spread Current bid-ask spread percentage
     * @param fundingRate Current funding rate
     * @return true if symbol is acceptable
     */
    public boolean passesBlacklistCriteria(String symbol, BigDecimal volume24h,
                                          BigDecimal spread, BigDecimal fundingRate) {
        // Check minimum volume
        long minVolume = strategyProperties.getMarketSelection().getBlacklist().getMin24hVolumeUsd();
        if (volume24h.compareTo(BigDecimal.valueOf(minVolume)) < 0) {
            log.debug("{} rejected: volume ${} below minimum ${}", symbol, volume24h, minVolume);
            return false;
        }

        // Check maximum spread
        BigDecimal maxSpread = strategyProperties.getMarketSelection().getBlacklist().getMaxSpreadPercent();
        if (spread.compareTo(maxSpread) > 0) {
            log.debug("{} rejected: spread {}% above maximum {}%", symbol, spread, maxSpread);
            return false;
        }

        // Check funding rate limits
        BigDecimal maxFundingRate = strategyProperties.getMarketSelection().getBlacklist().getMaxFundingRate();
        BigDecimal minFundingRate = strategyProperties.getMarketSelection().getBlacklist().getMinFundingRate();

        if (fundingRate.compareTo(maxFundingRate) > 0) {
            log.debug("{} rejected: funding rate {} above maximum {}", symbol, fundingRate, maxFundingRate);
            return false;
        }

        if (fundingRate.compareTo(minFundingRate) < 0) {
            log.debug("{} rejected: funding rate {} below minimum {}", symbol, fundingRate, minFundingRate);
            return false;
        }

        return true;
    }

    /**
     * Get priority score for a symbol based on tier
     *
     * @param symbol Symbol to score
     * @return Priority score (higher is better)
     */
    public int getPriorityScore(String symbol) {
        List<String> tier1 = strategyProperties.getMarketSelection().getTier1Pairs();
        List<String> tier2 = strategyProperties.getMarketSelection().getTier2Pairs();

        if (tier1.contains(symbol)) {
            return 100;
        } else if (tier2.contains(symbol)) {
            return 50;
        } else {
            return 10; // Tier 3
        }
    }

    /**
     * Filter and sort symbols by priority
     */
    public List<String> filterAndPrioritizePairs(List<String> symbols) {
        List<String> tier1 = new ArrayList<>();
        List<String> tier2 = new ArrayList<>();
        List<String> tier3 = new ArrayList<>();

        for (String symbol : symbols) {
            int priority = getPriorityScore(symbol);
            if (priority >= 100) {
                tier1.add(symbol);
            } else if (priority >= 50) {
                tier2.add(symbol);
            } else {
                tier3.add(symbol);
            }
        }

        // Combine tiers in priority order
        List<String> result = new ArrayList<>();
        result.addAll(tier1);
        result.addAll(tier2);
        result.addAll(tier3);

        return result;
    }

    /**
     * Check if symbol is in configured trading pairs
     */
    public boolean isConfiguredPair(String symbol) {
        List<String> tier1 = strategyProperties.getMarketSelection().getTier1Pairs();
        List<String> tier2 = strategyProperties.getMarketSelection().getTier2Pairs();

        return tier1.contains(symbol) || tier2.contains(symbol);
    }
}
