package com.bot.gift.x1000.martiangletradingbot.service.notification;

import com.bot.gift.x1000.martiangletradingbot.model.entity.Position;
import com.bot.gift.x1000.martiangletradingbot.model.entity.Trade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Notification Service
 * Sends alerts via multiple channels (Console, Telegram, Discord, Email)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    // TODO: Add Telegram, Discord, Email clients when configured

    /**
     * Send trade entry notification
     */
    @Async
    public void notifyTradeEntry(Trade trade) {
        String message = String.format(
            "🟢 ENTRY: %s %s @ $%s | Size: $%s | SL: $%s | TP: $%s",
            trade.getDirection(),
            trade.getSymbol(),
            trade.getAverageEntryPrice(),
            trade.getTotalPositionSize().multiply(trade.getAverageEntryPrice()),
            trade.getStopLoss(),
            !trade.getTakeProfitLevels().isEmpty() ? trade.getTakeProfitLevels().get(0) : "N/A"
        );

        sendNotification(message, NotificationLevel.INFO);
    }

    /**
     * Send trade exit notification
     */
    @Async
    public void notifyTradeExit(Trade trade, String reason) {
        String emoji = trade.getProfitLossUsdt().compareTo(java.math.BigDecimal.ZERO) > 0 ? "✅" : "❌";

        String message = String.format(
            "%s EXIT: %s %s | P/L: $%s (%.2f%%) | Reason: %s | Duration: %d min",
            emoji,
            trade.getDirection(),
            trade.getSymbol(),
            trade.getProfitLossUsdt(),
            trade.getProfitLossPercent(),
            reason,
            trade.getHoldingTimeMinutes()
        );

        sendNotification(message, NotificationLevel.INFO);
    }

    /**
     * Send partial exit notification
     */
    @Async
    public void notifyPartialExit(Position position, int percentClosed, String reason) {
        String message = String.format(
            "📊 PARTIAL EXIT: %s %s | Closed: %d%% | Reason: %s | Current P/L: $%s",
            position.getDirection(),
            position.getSymbol(),
            percentClosed,
            reason,
            position.getUnrealizedPnl()
        );

        sendNotification(message, NotificationLevel.INFO);
    }

    /**
     * Send daily limit reached notification
     */
    @Async
    public void notifyDailyLimitReached(String reason) {
        String message = String.format(
            "⚠️ DAILY LIMIT REACHED: %s | Trading stopped for today",
            reason
        );

        sendNotification(message, NotificationLevel.WARNING);
    }

    /**
     * Send error notification
     */
    @Async
    public void notifyError(String component, String error) {
        String message = String.format(
            "🔴 ERROR in %s: %s",
            component,
            error
        );

        sendNotification(message, NotificationLevel.ERROR);
    }

    /**
     * Send emergency exit notification
     */
    @Async
    public void notifyEmergencyExit(Position position, String reason) {
        String message = String.format(
            "🚨 EMERGENCY EXIT: %s %s | Reason: %s | P/L: $%s",
            position.getDirection(),
            position.getSymbol(),
            reason,
            position.getUnrealizedPnl()
        );

        sendNotification(message, NotificationLevel.CRITICAL);
    }

    /**
     * Send DCA entry notification
     */
    @Async
    public void notifyDcaEntry(Position position, int level) {
        String message = String.format(
            "📈 DCA ENTRY: %s %s | Level: %d/%d | New Avg: $%s",
            position.getDirection(),
            position.getSymbol(),
            level,
            position.getMaxDcaLevel(),
            position.getAverageEntryPrice()
        );

        sendNotification(message, NotificationLevel.INFO);
    }

    /**
     * Send notification to all configured channels
     */
    private void sendNotification(String message, NotificationLevel level) {
        // Console logging
        switch (level) {
            case INFO -> log.info(message);
            case WARNING -> log.warn(message);
            case ERROR, CRITICAL -> log.error(message);
        }

        // TODO: Send to Telegram if configured
        // sendToTelegram(message);

        // TODO: Send to Discord if configured
        // sendToDiscord(message);

        // TODO: Send to Email if configured (for CRITICAL/ERROR only)
        // if (level == NotificationLevel.CRITICAL || level == NotificationLevel.ERROR) {
        //     sendToEmail(message);
        // }
    }

    public enum NotificationLevel {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
}
