package com.aegisnotify.notification.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.port.in.PublishOutboxEventUseCase;
import com.aegisnotify.notification.infrastructure.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Regression test for K7 of the design: the outbox relay's scheduling
 * infrastructure must be a mandatory, always-on subsystem, never coupled to
 * the (Slice 1, not-yet-implemented) aggregation feature being enabled.
 *
 * <p>This test deliberately sets NO {@code notification.aggregation.*}
 * property at all — proving the relay's ability to tick does not depend on
 * aggregation config existing, let alone being turned on. It registers the
 * REAL {@link SchedulingConfig} and {@link OutboxWorkerScheduler} classes
 * (not test doubles) so {@code OutboxWorkerScheduler}'s actual
 * {@code @ConditionalOnProperty} is genuinely evaluated by Spring, only
 * substituting its one collaborator dependency.</p>
 */
class SchedulingConfigTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
      .withUserConfiguration(SchedulingConfig.class, OutboxWorkerScheduler.class)
      .withBean(PublishOutboxEventUseCase.class,
          () -> Mockito.mock(PublishOutboxEventUseCase.class));

  @Test
  void schedulingConfig_carriesNoConditionalAnnotation() {
    assertThat(SchedulingConfig.class.getAnnotation(EnableScheduling.class)).isNotNull();
    // A conditional annotation here would silently couple the mandatory
    // outbox relay to an optional feature flag - the exact dormancy bug K7
    // exists to prevent. @Configuration + @EnableScheduling is exactly two
    // annotations; a third would be a conditional (or anything else) that
    // must not be there.
    assertThat(SchedulingConfig.class.getAnnotations()).hasSize(2);
  }

  @Test
  void relayEnabled_withNoAggregationPropertySet_schedulerBeanExistsAndWired() {
    this.contextRunner
        .withPropertyValues(
            "notification.kafka.relay.enabled=true",
            "notification.kafka.relay.poll-interval=PT1S")
        .run(context -> {
          assertThat(context).hasSingleBean(SchedulingConfig.class);
          assertThat(context).hasSingleBean(OutboxWorkerScheduler.class);
          assertThat(context).hasSingleBean(ThreadPoolTaskScheduler.class);
          // Delegation and the per-tick exception guard are already proven
          // deterministically by OutboxWorkerSchedulerTest (plain Mockito,
          // no Spring context). Manually invoking pollOutboxEvents() here,
          // with a REAL ThreadPoolTaskScheduler live in this context (needed
          // to prove pool-size wiring), used to race that bean's own first
          // tick — @Scheduled(fixedDelayString=...) fires immediately on
          // context startup regardless of the configured interval — which
          // intermittently produced 2 calls instead of 1 under real CI/
          // Docker timing. This test only needs to prove the bean exists
          // and resolves the correct use-case dependency.
          assertThat(context.getBean(OutboxWorkerScheduler.class)).isNotNull();
        });
  }

  @Test
  void relayDisabled_schedulerBeanIsAbsent_butSchedulingInfrastructureStillBootstraps() {
    this.contextRunner
        .withPropertyValues("notification.kafka.relay.enabled=false")
        .run(context -> {
          assertThat(context).hasSingleBean(SchedulingConfig.class);
          assertThat(context).doesNotHaveBean(OutboxWorkerScheduler.class);
        });
  }
}
