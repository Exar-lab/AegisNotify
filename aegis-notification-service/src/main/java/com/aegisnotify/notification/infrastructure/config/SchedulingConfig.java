package com.aegisnotify.notification.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The single, unconditional scheduling bootstrap for the whole application
 * (K7 of the design).
 *
 * <p>Scheduling infrastructure must always be available: the outbox relay
 * ({@link com.aegisnotify.notification.infrastructure.scheduling
 * .OutboxWorkerScheduler}) is a mandatory subsystem, not an optional one.
 * Placing {@code @EnableScheduling} on an aggregation-scoped configuration
 * class instead would couple the relay's ability to tick at all to
 * aggregation being enabled — recreating the exact "outbox never ships
 * anything" dormancy bug this change exists to fix.</p>
 *
 * <p>Each individual {@code @Scheduled} task (this scheduler, and later the
 * aggregation flush poller) stays independently gated via its own
 * {@code @ConditionalOnProperty}; this class carries no such condition and
 * must never be given one.</p>
 *
 * <p>{@code spring.task.scheduling.pool.size} is set to {@code 2} in
 * {@code application.yml} because Spring's default {@link
 * org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler} is
 * single-threaded: without a second thread, a slow scheduled task would
 * queue every other scheduled task behind it.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
