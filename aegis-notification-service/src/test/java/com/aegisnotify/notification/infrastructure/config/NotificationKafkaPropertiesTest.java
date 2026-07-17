package com.aegisnotify.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class NotificationKafkaPropertiesTest {

  private static final String GROUP_ID = "notification-service";
  private static final String HIGH_PRIORITY = "high-priority-topic";
  private static final String MEDIUM_PRIORITY = "medium-priority-topic";
  private static final String LOW_PRIORITY = "low-priority-topic";
  private static final int PARTITIONS = 3;
  private static final short REPLICATION_FACTOR = 3;
  private static final int MIN_IN_SYNC_REPLICAS = 2;
  private static final String DLT_SUFFIX = "-dlt";
  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withInitializer(new RemoveKafkaPropertySourcesInitializer())
      .withInitializer(new ConfigDataApplicationContextInitializer())
      .withUserConfiguration(PropertiesConfiguration.class);
  private final ApplicationContextRunner placeholderContextRunner = new ApplicationContextRunner()
      .withInitializer(new ConfigDataApplicationContextInitializer())
      .withUserConfiguration(PropertiesConfiguration.class);

  @Test
  void loadsRealApplicationYamlAndBindsTopicProperties() {
    this.contextRunner.run(context -> {
      MutablePropertySources propertySources = context.getEnvironment().getPropertySources();
      assertThat(
          propertySources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
          .isFalse();
      assertThat(
          propertySources.contains(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME))
          .isFalse();
      assertThat(context.getEnvironment().getProperty("notification.kafka.topics.high-priority"))
          .isEqualTo(HIGH_PRIORITY);
      assertThat(context.getEnvironment().getProperty("notification.kafka.consumer.group-id"))
          .isEqualTo(GROUP_ID);

      NotificationKafkaProperties properties = context.getBean(NotificationKafkaProperties.class);

      assertThat(context).hasSingleBean(NotificationKafkaProperties.Registration.class);
      assertThat(properties.relay().enabled()).isFalse();
      assertThat(properties.consumer().enabled()).isFalse();
      assertThat(properties.consumer().groupId()).isEqualTo(GROUP_ID);
      assertThat(properties.topics().highPriority()).isEqualTo(HIGH_PRIORITY);
      assertThat(properties.topics().mediumPriority()).isEqualTo(MEDIUM_PRIORITY);
      assertThat(properties.topics().lowPriority()).isEqualTo(LOW_PRIORITY);
      assertThat(properties.topics().partitions()).isEqualTo(PARTITIONS);
      assertThat(properties.topics().replicationFactor()).isEqualTo(REPLICATION_FACTOR);
      assertThat(properties.topics().minInSyncReplicas()).isEqualTo(MIN_IN_SYNC_REPLICAS);
      assertThat(properties.topics().dltSuffix()).isEqualTo(DLT_SUFFIX);
      assertThat(properties.sourceTopics()).containsExactly(
          HIGH_PRIORITY,
          MEDIUM_PRIORITY,
          LOW_PRIORITY);
    });
  }

  @Test
  void realApplicationYamlBindsEveryNotificationKafkaPlaceholder() {
    this.placeholderContextRunner
        .withSystemProperties(
            "NOTIFICATION_KAFKA_RELAY_ENABLED=true",
            "NOTIFICATION_KAFKA_CONSUMER_ENABLED=true",
            "NOTIFICATION_KAFKA_CONSUMER_GROUP_ID=notification-contract-v3",
            "NOTIFICATION_KAFKA_HIGH_PRIORITY_TOPIC=contract.notifications.high",
            "NOTIFICATION_KAFKA_MEDIUM_PRIORITY_TOPIC=contract.notifications.medium",
            "NOTIFICATION_KAFKA_LOW_PRIORITY_TOPIC=contract.notifications.low",
            "NOTIFICATION_KAFKA_TOPIC_PARTITIONS=8",
            "NOTIFICATION_KAFKA_TOPIC_REPLICATION_FACTOR=5",
            "NOTIFICATION_KAFKA_TOPIC_MIN_IN_SYNC_REPLICAS=4",
            "NOTIFICATION_KAFKA_DLT_SUFFIX=.dead")
        .run(context -> {
          NotificationKafkaProperties properties =
              context.getBean(NotificationKafkaProperties.class);

          assertThat(properties.relay().enabled()).isTrue();
          assertThat(properties.consumer().enabled()).isTrue();
          assertThat(properties.consumer().groupId()).isEqualTo("notification-contract-v3");
          assertThat(properties.sourceTopics()).containsExactly(
              "contract.notifications.high",
              "contract.notifications.medium",
              "contract.notifications.low");
          assertThat(properties.topics().partitions()).isEqualTo(8);
          assertThat(properties.topics().replicationFactor()).isEqualTo((short) 5);
          assertThat(properties.topics().minInSyncReplicas()).isEqualTo(4);
          assertThat(properties.topics().dltSuffix()).isEqualTo(".dead");
        });
  }

  @Test
  void bindsNonDefaultTopicOverrides() {
    this.contextRunner
        .withPropertyValues(
            "notification.kafka.relay.enabled=true",
            "notification.kafka.consumer.enabled=true",
            "notification.kafka.consumer.group-id=notification-service-v2",
            "notification.kafka.topics.high-priority=notifications.high.v2",
            "notification.kafka.topics.medium-priority=notifications.medium.v2",
            "notification.kafka.topics.low-priority=notifications.low.v2",
            "notification.kafka.topics.partitions=6",
            "notification.kafka.topics.replication-factor=2",
            "notification.kafka.topics.min-in-sync-replicas=2",
            "notification.kafka.topics.dlt-suffix=.dlt")
        .run(context -> {
          NotificationKafkaProperties properties =
              context.getBean(NotificationKafkaProperties.class);

          assertThat(properties.relay().enabled()).isTrue();
          assertThat(properties.consumer().enabled()).isTrue();
          assertThat(properties.consumer().groupId()).isEqualTo("notification-service-v2");
          assertThat(properties.sourceTopics()).containsExactly(
              "notifications.high.v2",
              "notifications.medium.v2",
              "notifications.low.v2");
          assertThat(properties.topics().partitions()).isEqualTo(6);
          assertThat(properties.topics().replicationFactor()).isEqualTo((short) 2);
          assertThat(properties.topics().minInSyncReplicas()).isEqualTo(2);
          assertThat(properties.topics().dltSuffix()).isEqualTo(".dlt");
        });
  }

  @Test
  void productionPackageScanRegistersPropertiesAndAppliesValidation() {
    this.contextRunner
        .withPropertyValues("notification.kafka.consumer.group-id= ")
        .run(context ->
            assertThat(context.getStartupFailure()).hasStackTraceContaining(
                "notification.kafka.consumer.group-id must not be blank"));
  }


  @Configuration(proxyBeanMethods = false)
  @ComponentScan(
      basePackageClasses = NotificationKafkaProperties.class,
      useDefaultFilters = false,
      includeFilters = @ComponentScan.Filter(
          type = FilterType.ASSIGNABLE_TYPE,
          classes = NotificationKafkaProperties.Registration.class))
  static class PropertiesConfiguration {
  }

  private static final class RemoveKafkaPropertySourcesInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
      MutablePropertySources propertySources = context.getEnvironment().getPropertySources();
      propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
      propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    }
  }
}
