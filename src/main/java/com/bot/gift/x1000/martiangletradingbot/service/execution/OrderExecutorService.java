package com.bot.gift.x1000.martiangletradingbot.service.execution;

import com.bot.gift.x1000.martiangletradingbot.config.ExchangeProperties;
import com.bot.gift.x1000.martiangletradingbot.config.RiskProperties;
import com.bot.gift.x1000.martiangletradingbot.exchange.model.OrderRequest;
import com.bot.gift.x1000.martiangletradingbot.exchange.model.OrderResponse;
import com.bot.gift.x1000.martiangletradingbot.model.dto.EntrySignal;
import com.bot.gift.x1000.martiangletradingbot.model.entity.Position;
import com.bot.gift.x1000.martiangletradingbot.model.entity.Trade;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeDirection;
import com.bot.gift.x1000.martiangletradingbot.model.enums.TradeStatus;
import com.bot.gift.x1000.martiangletradingbot.repository.PositionRepository;
import com.bot.gift.x1000.martiangletradingbot.service.TradeLoggerService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Order Executor Service
 * Executes orders on Binance exchange with DCA support
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderExecutorService {

    private final ExchangeProperties exchangeProperties;
    private final RiskProperties riskProperties;
    private final TradeLoggerService tradeLoggerService;
    private final PositionRepository positionRepository;
    // private final BinanceClient binanceClient; // TODO: Inject when implementing actual Binance integration

    /**
     * Execute initial entry order
     *
     * @param signal Entry signal
     * @param positionSize Position size in USDT
     * @return Created trade and position
     */
    @Transactional
    @Retryable(value = Exception.class, maxAttempts = 4, backoff = @Backoff(delay = 2000, multiplier = 2))
    public Trade executeEntry(EntrySignal signal, BigDecimal positionSize) {
        log.info("Executing entry order for {} {} at {}, size: ${}",
            signal.getSymbol(), signal.getDirection(), signal.getEntryPrice(), positionSize);

        // Create order request
        OrderRequest orderRequest = buildMarketOrder(
            signal.getSymbol(),
            signal.getDirection() == TradeDirection.LONG ? "BUY" : "SELL",
            positionSize,
            signal.getEntryPrice()
        );

        // Execute order on exchange
        OrderResponse orderResponse = executeOrderOnExchange(orderRequest);

        // Create trade record
        Trade trade = createTradeFromOrder(signal, orderResponse, positionSize);
        trade = tradeLoggerService.createTrade(trade);

        // Create position for monitoring
        Position position = createPositionFromTrade(trade, signal);
        positionRepository.save(position);

        log.info("Entry executed: Trade {} created for {} with avg price {}",
            trade.getTradeId(), signal.getSymbol(), orderResponse.getAvgPrice());

        return trade;
    }

    /**
     * Execute DCA entry (additional position)
     *
     * @param position Existing position
     * @param dcaPrice DCA entry price
     * @param dcaSize DCA position size
     */
    @Transactional
    @Retryable(value = Exception.class, maxAttempts = 4, backoff = @Backoff(delay = 2000, multiplier = 2))
    public void executeDcaEntry(Position position, BigDecimal dcaPrice, BigDecimal dcaSize) {
        log.info("Executing DCA entry for {} at level {} price {}",
            position.getSymbol(), position.getDcaLevel() + 1, dcaPrice);

        // Create DCA order
        OrderRequest orderRequest = buildMarketOrder(
            position.getSymbol(),
            position.getDirection() == TradeDirection.LONG ? "BUY" : "SELL",
            dcaSize,
            dcaPrice
        );

        // Execute order
        OrderResponse orderResponse = executeOrderOnExchange(orderRequest);

        // Update position with new entry
        updatePositionWithDcaEntry(position, orderResponse, dcaSize);

        // Update trade record
        Trade trade = tradeLoggerService.updateTrade(
            updateTradeWithDcaEntry(position.getTradeId(), orderResponse)
        );

        log.info("DCA entry executed: {} at {}, new avg entry: {}",
            position.getSymbol(), orderResponse.getAvgPrice(), position.getAverageEntryPrice());
    }

    /**
     * Execute exit order (full or partial)
     *
     * @param position Position to exit
     * @param exitPercent Percentage of position to exit (0-100)
     * @param exitReason Reason for exit
     */
    @Transactional
    @Retryable(value = Exception.class, maxAttempts = 4, backoff = @Backoff(delay = 2000, multiplier = 2))
    public void executeExit(Position position, int exitPercent, String exitReason) {
        BigDecimal exitSize = position.getRemainingPositionSize()
            .multiply(BigDecimal.valueOf(exitPercent))
            .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        log.info("Executing exit for {} - {}% (${}) - Reason: {}",
            position.getSymbol(), exitPercent, exitSize, exitReason);

        // Create exit order
        OrderRequest orderRequest = buildMarketOrder(
            position.getSymbol(),
            position.getDirection() == TradeDirection.LONG ? "SELL" : "BUY",
            exitSize,
            position.getCurrentPrice() // Market order will use current price
        );

        // Execute order
        OrderResponse orderResponse = executeOrderOnExchange(orderRequest);

        // Update position
        updatePositionWithExit(position, orderResponse, exitSize);

        // Update trade record
        updateTradeWithExit(position.getTradeId(), orderResponse, exitReason);

        // If position fully closed, close trade
        if (position.getRemainingPositionSize().compareTo(BigDecimal.ZERO) == 0) {
            position.setStatus(TradeStatus.CLOSED);
            positionRepository.save(position);

            // Close trade in database
            Trade trade = tradeLoggerService.updateTrade(
                getTradeById(position.getTradeId())
            );
            tradeLoggerService.closeTrade(trade);

            log.info("Position fully closed: {}", position.getSymbol());
        } else {
            position.setStatus(TradeStatus.PARTIALLY_CLOSED);
            positionRepository.save(position);
            log.info("Position partially closed: {} - Remaining: ${}",
                position.getSymbol(), position.getRemainingPositionSize());
        }
    }

    /**
     * Build market order request
     */
    private OrderRequest buildMarketOrder(String symbol, String side, BigDecimal size, BigDecimal referencePrice) {
        // Calculate quantity based on size and price
        BigDecimal quantity = size.divide(referencePrice, 8, RoundingMode.HALF_UP);

        return OrderRequest.builder()
            .symbol(symbol)
            .side(side)
            .type("MARKET")
            .quantity(quantity)
            .timeInForce("GTC")
            .build();
    }

    /**
     * Execute order on exchange with circuit breaker protection
     * Fallback to orderExecutionFallback when circuit is open
     * TODO: Implement actual Binance API integration
     */
    @CircuitBreaker(name = "binanceExchange", fallbackMethod = "orderExecutionFallback")
    private OrderResponse executeOrderOnExchange(OrderRequest request) {
        // PLACEHOLDER: In production, this would call Binance API
        // For now, simulate successful order execution

        log.info("Executing order on exchange: {} {} {} qty: {}",
            request.getSymbol(), request.getSide(), request.getType(), request.getQuantity());

        // Simulate order execution
        OrderResponse response = OrderResponse.builder()
            .orderId("ORDER-" + System.currentTimeMillis())
            .symbol(request.getSymbol())
            .status("FILLED")
            .side(request.getSide())
            .type(request.getType())
            .price(request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO)
            .executedQty(request.getQuantity())
            .avgPrice(request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO)
            .transactTime(Instant.now())
            .clientOrderId("CLIENT-" + System.currentTimeMillis())
            .build();

        // TODO: Replace with actual Binance API call:
        // try {
        //     response = binanceClient.newOrder(request);
        // } catch (BinanceApiException e) {
        //     log.error("Order execution failed: {}", e.getMessage());
        //     throw new RuntimeException("Order execution failed", e);
        // }

        return response;
    }

    /**
     * Fallback method when circuit breaker is OPEN
     * Prevents orders from being placed when exchange is unreachable
     */
    private OrderResponse orderExecutionFallback(OrderRequest request, CallNotPermittedException exception) {
        log.error("Circuit breaker OPEN - Cannot execute order for {} {}. Exchange is temporarily unavailable.",
            request.getSymbol(), request.getSide());

        throw new RuntimeException("Exchange service is temporarily unavailable. Circuit breaker is OPEN. " +
            "Please try again later or check exchange connectivity.", exception);
    }

    /**
     * Fallback method for general exceptions during order execution
     */
    private OrderResponse orderExecutionFallback(OrderRequest request, Exception exception) {
        log.error("Order execution failed with exception for {} {}: {}",
            request.getSymbol(), request.getSide(), exception.getMessage());

        throw new RuntimeException("Order execution failed: " + exception.getMessage(), exception);
    }

    /**
     * Create trade from order response
     */
    private Trade createTradeFromOrder(EntrySignal signal, OrderResponse orderResponse, BigDecimal positionSize) {
        Trade trade = Trade.builder()
            .symbol(signal.getSymbol())
            .direction(signal.getDirection())
            .status(TradeStatus.OPEN)
            .entryTime(Instant.now())
            .entryPrices(new ArrayList<>(List.of(orderResponse.getAvgPrice())))
            .averageEntryPrice(orderResponse.getAvgPrice())
            .totalPositionSize(orderResponse.getExecutedQty())
            .remainingPositionSize(orderResponse.getExecutedQty())
            .initialStopLoss(signal.getStopLoss())
            .stopLoss(signal.getStopLoss())
            .takeProfitLevels(new ArrayList<>(List.of(
                signal.getTakeProfit1(),
                signal.getTakeProfit2(),
                signal.getTakeProfit3()
            )))
            .leverage(riskProperties.getDefaultLeverage())
            .indicatorsAtEntry(createIndicatorSnapshot(signal))
            .build();

        return trade;
    }

    /**
     * Create indicator snapshot from signal
     */
    private Trade.IndicatorSnapshot createIndicatorSnapshot(EntrySignal signal) {
        return new Trade.IndicatorSnapshot(
            null, // h4Ema34 - would need to pass from scanner
            null, // h4Ema89
            null, // h4Adx
            null, // h1Rsi
            signal.getRsi(),
            signal.getAtr(),
            signal.getEma21()
        );
    }

    /**
     * Create position from trade
     */
    private Position createPositionFromTrade(Trade trade, EntrySignal signal) {
        return Position.builder()
            .tradeId(trade.getTradeId())
            .symbol(trade.getSymbol())
            .direction(trade.getDirection())
            .status(TradeStatus.OPEN)
            .entryTime(trade.getEntryTime())
            .averageEntryPrice(trade.getAverageEntryPrice())
            .currentPrice(trade.getAverageEntryPrice())
            .totalPositionSize(trade.getTotalPositionSize())
            .remainingPositionSize(trade.getRemainingPositionSize())
            .currentStopLoss(trade.getStopLoss())
            .tp1Level(signal.getTakeProfit1())
            .tp2Level(signal.getTakeProfit2())
            .tp3Level(signal.getTakeProfit3())
            .tp1Hit(false)
            .tp2Hit(false)
            .dcaLevel(1)
            .maxDcaLevel(signal.getMaxDcaLevels())
            .nextDcaPrice(calculateNextDcaPrice(trade.getAverageEntryPrice(), signal))
            .build();
    }

    /**
     * Calculate next DCA entry price
     */
    private BigDecimal calculateNextDcaPrice(BigDecimal currentEntry, EntrySignal signal) {
        BigDecimal spacing = currentEntry
            .multiply(signal.getDcaSpacingPercent())
            .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

        if (signal.getDirection() == TradeDirection.LONG) {
            return currentEntry.subtract(spacing);
        } else {
            return currentEntry.add(spacing);
        }
    }

    /**
     * Update position with DCA entry
     */
    private void updatePositionWithDcaEntry(Position position, OrderResponse order, BigDecimal dcaSize) {
        // Calculate new average entry price
        BigDecimal totalValue = position.getAverageEntryPrice()
            .multiply(position.getTotalPositionSize())
            .add(order.getAvgPrice().multiply(order.getExecutedQty()));

        BigDecimal newTotalSize = position.getTotalPositionSize().add(order.getExecutedQty());
        BigDecimal newAvgPrice = totalValue.divide(newTotalSize, 8, RoundingMode.HALF_UP);

        position.setAverageEntryPrice(newAvgPrice);
        position.setTotalPositionSize(newTotalSize);
        position.setRemainingPositionSize(newTotalSize);
        position.setDcaLevel(position.getDcaLevel() + 1);

        // Calculate next DCA price if not at max level
        if (position.getDcaLevel() < position.getMaxDcaLevel()) {
            // TODO: Calculate next DCA price based on spacing
        }

        positionRepository.save(position);
    }

    /**
     * Update trade with DCA entry
     */
    private Trade updateTradeWithDcaEntry(String tradeId, OrderResponse order) {
        Trade trade = getTradeById(tradeId);

        // Add new entry price
        trade.getEntryPrices().add(order.getAvgPrice());

        // Recalculate average
        BigDecimal totalValue = BigDecimal.ZERO;
        for (BigDecimal price : trade.getEntryPrices()) {
            totalValue = totalValue.add(price);
        }
        trade.setAverageEntryPrice(totalValue.divide(
            BigDecimal.valueOf(trade.getEntryPrices().size()), 8, RoundingMode.HALF_UP
        ));

        trade.setTotalPositionSize(trade.getTotalPositionSize().add(order.getExecutedQty()));
        trade.setRemainingPositionSize(trade.getRemainingPositionSize().add(order.getExecutedQty()));

        return trade;
    }

    /**
     * Update position with exit
     */
    private void updatePositionWithExit(Position position, OrderResponse order, BigDecimal exitSize) {
        position.setRemainingPositionSize(
            position.getRemainingPositionSize().subtract(order.getExecutedQty())
        );

        // Calculate P/L
        BigDecimal priceDiff;
        if (position.getDirection() == TradeDirection.LONG) {
            priceDiff = order.getAvgPrice().subtract(position.getAverageEntryPrice());
        } else {
            priceDiff = position.getAverageEntryPrice().subtract(order.getAvgPrice());
        }

        BigDecimal pnl = priceDiff.multiply(order.getExecutedQty());
        BigDecimal currentUnrealized = position.getUnrealizedPnl() != null ? position.getUnrealizedPnl() : BigDecimal.ZERO;
        position.setUnrealizedPnl(currentUnrealized.add(pnl));

        positionRepository.save(position);
    }

    /**
     * Update trade with exit
     */
    private void updateTradeWithExit(String tradeId, OrderResponse order, String exitReason) {
        Trade trade = getTradeById(tradeId);

        // Add exit price
        trade.getExitPrices().add(order.getAvgPrice());

        // Update remaining position
        trade.setRemainingPositionSize(
            trade.getRemainingPositionSize().subtract(order.getExecutedQty())
        );

        // If fully closed, set exit time and calculate final metrics
        if (trade.getRemainingPositionSize().compareTo(BigDecimal.ZERO) == 0) {
            trade.setExitTime(Instant.now());

            // Calculate average exit price
            BigDecimal totalExitValue = BigDecimal.ZERO;
            for (BigDecimal price : trade.getExitPrices()) {
                totalExitValue = totalExitValue.add(price);
            }
            trade.setAverageExitPrice(totalExitValue.divide(
                BigDecimal.valueOf(trade.getExitPrices().size()), 8, RoundingMode.HALF_UP
            ));

            // Calculate final P/L
            BigDecimal priceDiff;
            if (trade.getDirection() == TradeDirection.LONG) {
                priceDiff = trade.getAverageExitPrice().subtract(trade.getAverageEntryPrice());
            } else {
                priceDiff = trade.getAverageEntryPrice().subtract(trade.getAverageExitPrice());
            }

            trade.setProfitLossUsdt(priceDiff.multiply(trade.getTotalPositionSize()));
        }

        tradeLoggerService.updateTrade(trade);
    }

    /**
     * Get trade by ID (placeholder)
     */
    private Trade getTradeById(String tradeId) {
        // TODO: Implement actual repository lookup
        return new Trade();
    }
}
