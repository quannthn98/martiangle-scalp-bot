package com.bot.gift.x1000.martiangletradingbot.service.monitoring;

import com.bot.gift.x1000.martiangletradingbot.config.StrategyProperties;
import com.bot.gift.x1000.martiangletradingbot.indicator.ADXIndicator;
import com.bot.gift.x1000.martiangletradingbot.indicator.EMAIndicator;
import com.bot.gift.x1000.martiangletradingbot.model.dto.TrendAnalysis;
import com.bot.gift.x1000.martiangletradingbot.model.entity.Candle;
import com.bot.gift.x1000.martiangletradingbot.model.entity.Position;
import com.bot.gift.x1000.martiangletradingbot.model.enums.Timeframe;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeDirection;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TrendDirection;
import com.bot.gift.x1000.martiangletradingbot.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Trend Confirmation Service
 * Re-validates trend direction every 5 minutes for open positions
 * Closes positions when trend reverses
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TrendConfirmationService {

    private final CandleRepository candleRepository;
    private final EMAIndicator emaIndicator;
    private final ADXIndicator adxIndicator;
    private final StrategyProperties strategyProperties;

    /**
     * Check if the current trend still supports the open position
     *
     * @param position Open position to check
     * @return true if trend is still valid, false if trend has reversed
     */
    public boolean isTrendStillValid(Position position) {
        log.debug("Checking trend confirmation for {} {}", position.getSymbol(), position.getDirection());

        // Check H4 trend (primary trend)
        TrendConfirmation h4Confirmation = checkH4Trend(position.getSymbol(), position.getDirection());

        if (!h4Confirmation.isValid()) {
            log.warn("H4 trend reversed for {} {} - Reason: {}",
                position.getSymbol(), position.getDirection(), h4Confirmation.reason());
            return false;
        }

        // Check H1 trend (secondary confirmation)
        TrendConfirmation h1Confirmation = checkH1Trend(position.getSymbol(), position.getDirection());

        if (!h1Confirmation.isValid()) {
            log.warn("H1 trend reversed for {} {} - Reason: {}",
                position.getSymbol(), position.getDirection(), h1Confirmation.reason());
            return false;
        }

        log.debug("Trend still valid for {} {}", position.getSymbol(), position.getDirection());
        return true;
    }

    /**
     * Check H4 trend confirmation
     */
    private TrendConfirmation checkH4Trend(String symbol, TradeDirection positionDirection) {
        // Fetch recent H4 candles
        List<Candle> candles = candleRepository.findRecentCandles(
            symbol,
            Timeframe.H4,
            100
        );

        if (candles.size() < 100) {
            return new TrendConfirmation(false, "Insufficient H4 candle data");
        }

        // Calculate current EMA values
        List<BigDecimal> closePrices = candles.stream()
            .map(Candle::getClose)
            .toList();

        List<BigDecimal> ema34Values = emaIndicator.calculate(closePrices,
            strategyProperties.getIndicators().getEma().getFast());
        List<BigDecimal> ema89Values = emaIndicator.calculate(closePrices,
            strategyProperties.getIndicators().getEma().getSlow());

        BigDecimal currentEma34 = ema34Values.get(ema34Values.size() - 1);
        BigDecimal currentEma89 = ema89Values.get(ema89Values.size() - 1);
        BigDecimal currentPrice = candles.get(candles.size() - 1).getClose();

        // Check EMA alignment based on position direction
        if (positionDirection == TradeDirection.LONG) {
            // For LONG: Price > EMA34 > EMA89 (bullish)
            if (currentPrice.compareTo(currentEma34) < 0) {
                return new TrendConfirmation(false, "Price dropped below EMA34 on H4");
            }
            if (currentEma34.compareTo(currentEma89) < 0) {
                return new TrendConfirmation(false, "EMA34 crossed below EMA89 on H4");
            }
        } else {
            // For SHORT: Price < EMA34 < EMA89 (bearish)
            if (currentPrice.compareTo(currentEma34) > 0) {
                return new TrendConfirmation(false, "Price moved above EMA34 on H4");
            }
            if (currentEma34.compareTo(currentEma89) > 0) {
                return new TrendConfirmation(false, "EMA34 crossed above EMA89 on H4");
            }
        }

        // Check ADX strength
        ADXIndicator.ADXResult adxResult = adxIndicator.calculate(candles);
        BigDecimal h4AdxThreshold = strategyProperties.getIndicators().getAdx().getH4Threshold();

        if (adxResult.adx().compareTo(h4AdxThreshold) < 0) {
            return new TrendConfirmation(false,
                String.format("H4 ADX weakened below threshold: %.2f < %.2f",
                    adxResult.adx(), h4AdxThreshold));
        }

        // Check DI alignment for trend direction
        if (positionDirection == TradeDirection.LONG) {
            if (adxResult.plusDI().compareTo(adxResult.minusDI()) < 0) {
                return new TrendConfirmation(false, "+DI crossed below -DI on H4 (trend reversal)");
            }
        } else {
            if (adxResult.minusDI().compareTo(adxResult.plusDI()) < 0) {
                return new TrendConfirmation(false, "-DI crossed below +DI on H4 (trend reversal)");
            }
        }

        return new TrendConfirmation(true, "H4 trend confirmed");
    }

    /**
     * Check H1 trend confirmation
     */
    private TrendConfirmation checkH1Trend(String symbol, TradeDirection positionDirection) {
        // Fetch recent H1 candles
        List<Candle> candles = candleRepository.findRecentCandles(
            symbol,
            Timeframe.H1,
            100
        );

        if (candles.size() < 100) {
            return new TrendConfirmation(false, "Insufficient H1 candle data");
        }

        // Calculate current EMA values
        List<BigDecimal> closePrices = candles.stream()
            .map(Candle::getClose)
            .toList();

        List<BigDecimal> ema34Values = emaIndicator.calculate(closePrices,
            strategyProperties.getIndicators().getEma().getFast());
        List<BigDecimal> ema89Values = emaIndicator.calculate(closePrices,
            strategyProperties.getIndicators().getEma().getSlow());

        BigDecimal currentEma34 = ema34Values.get(ema34Values.size() - 1);
        BigDecimal currentEma89 = ema89Values.get(ema89Values.size() - 1);
        BigDecimal currentPrice = candles.get(candles.size() - 1).getClose();

        // Check EMA alignment
        if (positionDirection == TradeDirection.LONG) {
            if (currentPrice.compareTo(currentEma34) < 0) {
                return new TrendConfirmation(false, "Price dropped below EMA34 on H1");
            }
            if (currentEma34.compareTo(currentEma89) < 0) {
                return new TrendConfirmation(false, "EMA34 crossed below EMA89 on H1");
            }
        } else {
            if (currentPrice.compareTo(currentEma34) > 0) {
                return new TrendConfirmation(false, "Price moved above EMA34 on H1");
            }
            if (currentEma34.compareTo(currentEma89) > 0) {
                return new TrendConfirmation(false, "EMA34 crossed above EMA89 on H1");
            }
        }

        // Check ADX strength
        ADXIndicator.ADXResult adxResult = adxIndicator.calculate(candles);
        BigDecimal h1AdxThreshold = strategyProperties.getIndicators().getAdx().getH1Threshold();

        if (adxResult.adx().compareTo(h1AdxThreshold) < 0) {
            return new TrendConfirmation(false,
                String.format("H1 ADX weakened below threshold: %.2f < %.2f",
                    adxResult.adx(), h1AdxThreshold));
        }

        // Check DI alignment
        if (positionDirection == TradeDirection.LONG) {
            if (adxResult.plusDI().compareTo(adxResult.minusDI()) < 0) {
                return new TrendConfirmation(false, "+DI crossed below -DI on H1 (trend reversal)");
            }
        } else {
            if (adxResult.minusDI().compareTo(adxResult.plusDI()) < 0) {
                return new TrendConfirmation(false, "-DI crossed below +DI on H1 (trend reversal)");
            }
        }

        return new TrendConfirmation(true, "H1 trend confirmed");
    }

    /**
     * Get trend strength score (0-100)
     * Used to determine if we should tighten stop loss or partially exit
     */
    public int getTrendStrengthScore(Position position) {
        List<Candle> h4Candles = candleRepository.findRecentCandles(
            position.getSymbol(),
            Timeframe.H4,
            100
        );

        if (h4Candles.size() < 100) {
            return 0;
        }

        int score = 50; // Base score

        // Calculate EMAs
        List<BigDecimal> closePrices = h4Candles.stream().map(Candle::getClose).toList();
        List<BigDecimal> ema34Values = emaIndicator.calculate(closePrices,
            strategyProperties.getIndicators().getEma().getFast());
        List<BigDecimal> ema89Values = emaIndicator.calculate(closePrices,
            strategyProperties.getIndicators().getEma().getSlow());

        BigDecimal currentEma34 = ema34Values.get(ema34Values.size() - 1);
        BigDecimal currentEma89 = ema89Values.get(ema89Values.size() - 1);
        BigDecimal currentPrice = h4Candles.get(h4Candles.size() - 1).getClose();

        // Calculate EMA separation (stronger trend = wider separation)
        BigDecimal emaSeparation = currentEma34.subtract(currentEma89).abs()
            .divide(currentEma89, 8, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        if (emaSeparation.compareTo(BigDecimal.valueOf(2.0)) > 0) {
            score += 20; // Strong separation
        } else if (emaSeparation.compareTo(BigDecimal.valueOf(1.0)) > 0) {
            score += 10; // Moderate separation
        }

        // Check ADX strength
        ADXIndicator.ADXResult adxResult = adxIndicator.calculate(h4Candles);
        if (adxResult.adx().compareTo(BigDecimal.valueOf(30)) > 0) {
            score += 20; // Very strong trend
        } else if (adxResult.adx().compareTo(BigDecimal.valueOf(25)) > 0) {
            score += 10; // Strong trend
        }

        // Check price distance from EMA34
        BigDecimal priceDistance = currentPrice.subtract(currentEma34).abs()
            .divide(currentEma34, 8, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        if (priceDistance.compareTo(BigDecimal.valueOf(1.0)) < 0) {
            score += 10; // Price close to EMA (healthy trend)
        } else if (priceDistance.compareTo(BigDecimal.valueOf(3.0)) > 0) {
            score -= 10; // Price too far from EMA (overextended)
        }

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Trend confirmation result
     */
    public record TrendConfirmation(
        boolean isValid,
        String reason
    ) {}
}
