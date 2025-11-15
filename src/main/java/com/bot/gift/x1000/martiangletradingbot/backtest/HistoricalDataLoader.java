package com.bot.gift.x1000.martiangletradingbot.backtest;

import com.bot.gift.x1000.martiangletradingbot.model.entity.Candle;
import com.bot.gift.x1000.martiangletradingbot.model.enums.Timeframe;
import com.bot.gift.x1000.martiangletradingbot.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Historical Data Loader
 * Loads historical candle data from various sources for backtesting
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HistoricalDataLoader {

    private final CandleRepository candleRepository;

    /**
     * Load historical candles from database
     *
     * @param symbol Trading pair symbol
     * @param timeframe Candle timeframe
     * @param startTime Start time for data
     * @param endTime End time for data
     * @return List of candles
     */
    public List<Candle> loadFromDatabase(String symbol, Timeframe timeframe,
                                          Instant startTime, Instant endTime) {
        log.info("Loading historical data from database: {} {} from {} to {}",
            symbol, timeframe, startTime, endTime);

        List<Candle> candles = candleRepository.findBySymbolAndTimeframeBetween(
            symbol, timeframe, startTime, endTime
        );

        log.info("Loaded {} candles from database", candles.size());
        return candles;
    }

    /**
     * Load historical candles from CSV file
     * Expected CSV format: timestamp,open,high,low,close,volume
     *
     * @param filePath Path to CSV file
     * @param symbol Symbol for the candles
     * @param timeframe Timeframe for the candles
     * @return List of candles
     */
    public List<Candle> loadFromCsv(Path filePath, String symbol, Timeframe timeframe) {
        log.info("Loading historical data from CSV: {} for {}", filePath, symbol);

        List<Candle> candles = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                // Skip header row if present
                if (isFirstLine) {
                    isFirstLine = false;
                    if (line.toLowerCase().contains("timestamp") ||
                        line.toLowerCase().contains("open")) {
                        continue;
                    }
                }

                String[] parts = line.split(",");
                if (parts.length < 6) {
                    log.warn("Skipping invalid CSV line: {}", line);
                    continue;
                }

                try {
                    long timestamp = Long.parseLong(parts[0].trim());
                    Instant openTime = Instant.ofEpochMilli(timestamp);

                    Candle candle = Candle.builder()
                        .symbol(symbol)
                        .timeframe(timeframe)
                        .openTime(openTime)
                        .closeTime(openTime.plusMillis(timeframe.getIntervalMinutes() * 60 * 1000L))
                        .open(new BigDecimal(parts[1].trim()))
                        .high(new BigDecimal(parts[2].trim()))
                        .low(new BigDecimal(parts[3].trim()))
                        .close(new BigDecimal(parts[4].trim()))
                        .volume(new BigDecimal(parts[5].trim()))
                        .build();

                    candles.add(candle);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse CSV line: {} - {}", line, e.getMessage());
                }
            }

            // Sort candles by time
            candles.sort(Comparator.comparing(Candle::getOpenTime));

            log.info("Loaded {} candles from CSV", candles.size());

        } catch (IOException e) {
            log.error("Failed to load CSV file: {}", e.getMessage());
            throw new RuntimeException("Failed to load historical data from CSV", e);
        }

        return candles;
    }

    /**
     * Load historical candles from Binance API
     * This is a placeholder for actual Binance API integration
     *
     * @param symbol Trading pair symbol
     * @param timeframe Candle timeframe
     * @param startTime Start time for data
     * @param endTime End time for data
     * @return List of candles
     */
    public List<Candle> loadFromBinance(String symbol, Timeframe timeframe,
                                         Instant startTime, Instant endTime) {
        log.info("Loading historical data from Binance: {} {} from {} to {}",
            symbol, timeframe, startTime, endTime);

        // TODO: Implement actual Binance API integration
        // This would use BinanceClient to fetch historical klines
        // Example:
        // List<List<Object>> klines = binanceClient.getKlines(
        //     symbol, timeframe.getBinanceInterval(),
        //     startTime.toEpochMilli(), endTime.toEpochMilli(), 1000
        // );
        // return convertToCandles(klines, symbol, timeframe);

        log.warn("Binance API integration not yet implemented - returning empty list");
        return new ArrayList<>();
    }

    /**
     * Generate sample candles for testing (when no historical data available)
     *
     * @param symbol Trading pair symbol
     * @param timeframe Candle timeframe
     * @param count Number of candles to generate
     * @param startPrice Starting price
     * @param trendDirection 1 for uptrend, -1 for downtrend, 0 for sideways
     * @return List of sample candles
     */
    public List<Candle> generateSampleData(String symbol, Timeframe timeframe,
                                            int count, double startPrice, int trendDirection) {
        log.info("Generating {} sample candles for {} (trend: {})",
            count, symbol, trendDirection);

        List<Candle> candles = new ArrayList<>();
        long intervalMs = timeframe.getIntervalMinutes() * 60 * 1000L;
        Instant currentTime = Instant.now().minusMillis(intervalMs * count);

        double currentPrice = startPrice;

        for (int i = 0; i < count; i++) {
            // Add trend movement
            double trendMove = trendDirection * (Math.random() * 50);

            // Add random volatility
            double volatility = (Math.random() - 0.5) * 100;

            double open = currentPrice;
            double close = open + trendMove + volatility;

            // Ensure realistic high/low
            double high = Math.max(open, close) * (1 + Math.random() * 0.005);
            double low = Math.min(open, close) * (1 - Math.random() * 0.005);

            Candle candle = Candle.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .openTime(currentTime)
                .closeTime(currentTime.plusMillis(intervalMs))
                .open(BigDecimal.valueOf(open))
                .high(BigDecimal.valueOf(high))
                .low(BigDecimal.valueOf(low))
                .close(BigDecimal.valueOf(close))
                .volume(BigDecimal.valueOf(1000 + Math.random() * 500))
                .build();

            candles.add(candle);

            currentPrice = close;
            currentTime = currentTime.plusMillis(intervalMs);
        }

        return candles;
    }

    /**
     * Validate loaded candle data
     */
    public boolean validateCandleData(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            log.error("No candles loaded");
            return false;
        }

        // Check for gaps in data
        for (int i = 1; i < candles.size(); i++) {
            Candle prev = candles.get(i - 1);
            Candle curr = candles.get(i);

            if (curr.getOpenTime().isBefore(prev.getOpenTime())) {
                log.error("Candles not in chronological order at index {}", i);
                return false;
            }
        }

        log.info("Candle data validation passed");
        return true;
    }
}
