package com.aegisnotify.notification.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.application.port.in.FlushAggregationWindowsUseCase;
import com.aegisnotify.notification.infrastructure.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;

/**
 * Regression coverage for tasks 1.26/1.27: with {@code
 * notification.aggregation.enabled=false} (the shipped default), no {@link
 * AggregationWindowScheduler} bean is created at all — proving the feature
 * is fully inert at runtime, exactly mirroring {@code
 * OutboxWorkerScheduler}'s own {@code @ConditionalOnProperty} pattern
 * (verified together with {@code SchedulingConfigTest}'s K7 proof that the
 * relay's own scheduling infrastructure never depends on this flag).
 *
 * <p>A mocked {@link TaskScheduler} bean is registered so {@code
 * @EnableScheduling}'s {@code ScheduledAnnotationBeanPostProcessor} uses it
 * instead of creating its own live local scheduler — without this, {@code
 * @Scheduled(fixedDelayString=...)} fires its first tick immediately on
 * context startup regardless of the configured interval (not just after it
 * elapses), which previously raced this test's own manual invocation of the
 * same method under real CI/Docker timing and forced the assertion down to
 * {@code atLeastOnce()} to tolerate it. Substituting the scheduler
 * eliminates the race outright — the manual call is now the ONLY
 * invocation, an assertion no faster/slower CI can ever flake.</p>
 */
class AggregationWindowSchedulerConditionalTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(SchedulingConfig.class, AggregationWindowScheduler.class)
      .withBean(FlushAggregationWindowsUseCase.class,
          () -> Mockito.mock(FlushAggregationWindowsUseCase.class))
      .withBean(TaskScheduler.class, () -> Mockito.mock(TaskScheduler.class));

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
