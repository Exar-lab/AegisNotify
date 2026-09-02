package com.aegisnotify.notification.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.port.in.FlushAggregationWindowsUseCase;
import com.aegisnotify.notification.infrastructure.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Regression coverage for tasks 1.26/1.27: with {@code
 * notification.aggregation.enabled=false} (the shipped default), no {@link
 * AggregationWindowScheduler} bean is created at all — proving the feature
 * is fully inert at runtime, exactly mirroring {@code
 * OutboxWorkerScheduler}'s own {@code @ConditionalOnProperty} pattern
 * (verified together with {@code SchedulingConfigTest}'s K7 proof that the
 * relay's own scheduling infrastructure never depends on this flag).
 *
 * <p>Deliberately does NOT register {@code TaskSchedulingAutoConfiguration}
 * or invoke the scheduler's method here: proving delegation and the per-tick
 * exception guard is {@link AggregationWindowSchedulerTest}'s job (plain
 * Mockito, no Spring context, fully deterministic). Mixing a live {@code
 * @Scheduled} tick into this class's context previously raced a manual
 * invocation of the same method, forcing the assertion down to {@code
 * atLeastOnce()} to tolerate it — this class now only proves bean wiring
 * (conditional presence/absence + the bean resolves the correct use-case
 * dependency), which needs no real ticking scheduler at all.</p>
 */
class AggregationWindowSchedulerConditionalTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(SchedulingConfig.class, AggregationWindowScheduler.class)
      .withBean(FlushAggregationWindowsUseCase.class,
          () -> Mockito.mock(FlushAggregationWindowsUseCase.class));

  @Test
  void aggregationDisabled_schedulerBeanIsAbsent() {
    this.contextRunner
        .withPropertyValues("notification.aggregation.enabled=false")
        .run(context -> {
          assertThat(context).hasSingleBean(SchedulingConfig.class);
          assertThat(context).doesNotHaveBean(AggregationWindowScheduler.class);
        });
  }

  @Test
  void aggregationDisabled_noPropertyAtAll_schedulerBeanIsAbsent() {
    this.contextRunner
        .run(context -> assertThat(context).doesNotHaveBean(AggregationWindowScheduler.class));
  }

  @Test
  void aggregationEnabled_schedulerBeanExists_wiredToRealUseCaseBean() {
    this.contextRunner
        .withPropertyValues("notification.aggregation.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(AggregationWindowScheduler.class);

          AggregationWindowScheduler scheduler = context.getBean(AggregationWindowScheduler.class);
          scheduler.flushExpiredWindows();
          Mockito.verify(context.getBean(FlushAggregationWindowsUseCase.class))
              .flushExpiredWindows();
        });
  }
}
