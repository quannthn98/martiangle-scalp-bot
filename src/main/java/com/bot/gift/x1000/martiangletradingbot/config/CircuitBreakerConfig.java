package com.bot.gift.x1000.martiangletradingbot.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit Breaker Configuration
 * Configures circuit breakers for exchange API calls to prevent cascading failures
 */
@Slf4j
@Configuration
public class CircuitBreakerConfig {

    /**
     * Registry event consumer for circuit breaker monitoring
     */
    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
                log.info("Circuit breaker {} added", circuitBreaker.getName());

                // Add event listeners
                circuitBreaker.getEventPublisher()
                    .onSuccess(event -> log.debug("Circuit breaker {} - Call succeeded", circuitBreaker.getName()))
                    .onError(event -> log.warn("Circuit breaker {} - Call failed: {}",
                        circuitBreaker.getName(), event.getThrowable().getMessage()))
                    .onStateTransition(event -> log.warn("Circuit breaker {} state changed from {} to {}",
                        circuitBreaker.getName(), event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                    .onSlowCallRateExceeded(event -> log.warn("Circuit breaker {} - Slow call rate exceeded: {}%",
                        circuitBreaker.getName(), event.getSlowCallRate()))
                    .onFailureRateExceeded(event -> log.error("Circuit breaker {} - Failure rate exceeded: {}%",
                        circuitBreaker.getName(), event.getFailureRate()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {
                log.info("Circuit breaker {} removed", entryRemoveEvent.getRemovedEntry().getName());
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
                log.info("Circuit breaker {} replaced", entryReplacedEvent.getNewEntry().getName());
            }
        };
    }

    /**
     * Custom circuit breaker configuration for critical exchange operations
     */
    @Bean
    public CircuitBreakerConfig criticalExchangeCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(
                java.net.ConnectException.class,
                java.net.SocketTimeoutException.class,
                java.io.IOException.class
            )
            .build();
    }

    /**
     * Circuit breaker configuration for market data operations
     */
    @Bean
    public CircuitBreakerConfig marketDataCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(15)
            .minimumNumberOfCalls(8)
            .failureRateThreshold(40)
            .waitDurationInOpenState(Duration.ofSeconds(45))
            .permittedNumberOfCallsInHalfOpenState(5)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
    }
}
