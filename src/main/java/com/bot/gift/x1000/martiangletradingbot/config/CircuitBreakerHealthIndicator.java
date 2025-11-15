package com.bot.gift.x1000.martiangletradingbot.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Circuit Breaker Health Indicator
 * Exposes circuit breaker states via Spring Boot Actuator health endpoint
 */
@Component
@RequiredArgsConstructor
public class CircuitBreakerHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        boolean allClosed = true;

        // Check all registered circuit breakers
        for (CircuitBreaker circuitBreaker : circuitBreakerRegistry.getAllCircuitBreakers()) {
            String name = circuitBreaker.getName();
            CircuitBreaker.State state = circuitBreaker.getState();
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();

            Map<String, Object> cbDetails = new HashMap<>();
            cbDetails.put("state", state.toString());
            cbDetails.put("failureRate", String.format("%.2f%%", metrics.getFailureRate()));
            cbDetails.put("numberOfSuccessfulCalls", metrics.getNumberOfSuccessfulCalls());
            cbDetails.put("numberOfFailedCalls", metrics.getNumberOfFailedCalls());
            cbDetails.put("numberOfBufferedCalls", metrics.getNumberOfBufferedCalls());
            cbDetails.put("numberOfNotPermittedCalls", metrics.getNumberOfNotPermittedCalls());

            details.put(name, cbDetails);

            // Check if any circuit breaker is not CLOSED
            if (state != CircuitBreaker.State.CLOSED) {
                allClosed = false;
            }
        }

        if (allClosed) {
            return Health.up()
                .withDetail("circuitBreakers", details)
                .withDetail("status", "All circuit breakers are CLOSED")
                .build();
        } else {
            return Health.down()
                .withDetail("circuitBreakers", details)
                .withDetail("status", "One or more circuit breakers are OPEN or HALF_OPEN")
                .build();
        }
    }
}
