package com.aegisnotify.notification.infrastructure.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.internals.Topic;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/** External configuration for the notification Kafka runtime and its six-topic topology. */
@ConfigurationProperties(prefix = "notification.kafka")
public record NotificationKafkaProperties(
    @NestedConfigurationProperty Relay relay,
    @NestedConfigurationProperty Consumer consumer,
    @NestedConfigurationProperty Topics topics) {

  /**
   * Creates and validates the notification Kafka runtime and topic contract.
   *
   * @param relay relay activation settings
   * @param consumer consumer activation and group settings
   * @param topics source and dead-letter topic settings
   */
  @ConstructorBinding
  public NotificationKafkaProperties(Relay relay, Consumer consumer, Topics topics) {
    this.relay = relay == null ? new Relay() : relay;
    this.consumer = consumer == null ? new Consumer() : consumer;
    this.topics = topics == null ? new Topics() : topics;
    validate();
  }

  public NotificationKafkaProperties(Consumer consumer, Topics topics) {
    this(new Relay(), consumer, topics);
  }

  public void validate() {
    requireText(consumer.groupId(), "notification.kafka.consumer.group-id");
    requirePositive(topics.partitions(), "notification.kafka.topics.partitions");
    requirePositive(topics.replicationFactor(), "notification.kafka.topics.replication-factor");
    requirePositive(topics.minInSyncReplicas(),
        "notification.kafka.topics.min-in-sync-replicas");
    if (topics.minInSyncReplicas() > topics.replicationFactor()) {
      throw new IllegalStateException(
          "notification.kafka.topics.min-in-sync-replicas must not exceed "
              + "notification.kafka.topics.replication-factor");
    }

    validateTopicNames(
        sourceTopics(),
        new String[] {
            "notification.kafka.topics.high-priority",
            "notification.kafka.topics.medium-priority",
            "notification.kafka.topics.low-priority"
        },
        requireText(topics.dltSuffix(), "notification.kafka.topics.dlt-suffix"));
  }

  public String[] sourceTopics() {
    return new String[] {
        topics.highPriority(),
        topics.mediumPriority(),
        topics.lowPriority()
    };
  }

  public String dltTopicFor(String sourceTopic) {
    return sourceTopic + topics.dltSuffix();
  }

  private static String requireText(String value, String propertyName) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException(propertyName + " must not be blank");
    }
    return value;
  }

  private static void requirePositive(int value, String propertyName) {
    if (value <= 0) {
      throw new IllegalStateException(propertyName + " must be greater than zero");
    }
  }

  private static String validateTopicName(String topicName, String propertyName) {
    try {
      Topic.validate(topicName);
      return topicName;
    } catch (InvalidTopicException ex) {
      throw new IllegalArgumentException(
          propertyName + " must be a valid Kafka topic name: " + topicName, ex);
    }
  }

  private static void validateTopicNames(
      String[] sourceTopics,
      String[] propertyNames,
      String dltSuffix) {
    Set<String> validatedTopics = new LinkedHashSet<>();
    for (int i = 0; i < sourceTopics.length; i++) {
      String topicName = requireText(sourceTopics[i], propertyNames[i]);
      validatedTopics.add(validateTopicName(topicName, propertyNames[i]));
      validatedTopics.add(validateTopicName(topicName + dltSuffix, propertyNames[i] + "-dlt"));
    }

    if (validatedTopics.size() != sourceTopics.length * 2) {
      throw new IllegalStateException("notification.kafka topics must resolve to six distinct "
          + "topic names: " + validatedTopics + " using suffix '" + dltSuffix + "'");
    }
  }

  /**
   * Controls consumer activation and group membership.
   *
   * @param enabled whether notification consumption is enabled
   * @param groupId notification consumer group identifier
   */
  public record Consumer(boolean enabled, String groupId) {

    @ConstructorBinding
    public Consumer {
      groupId = requireText(groupId, "notification.kafka.consumer.group-id");
    }

    public Consumer(String groupId) {
      this(false, groupId);
    }

    public Consumer() {
      this(false, "notification-service");
    }
  }

  /**
   * Controls relay activation independently from consumer activation.
   *
   * @param enabled whether outbox relay processing is enabled
   */
  public record Relay(boolean enabled) {

    @ConstructorBinding
    public Relay {
    }

    public Relay() {
      this(false);
    }
  }

  /**
   * Defines all source and dead-letter topics with their durability settings.
   *
   * @param highPriority high-priority source topic
   * @param mediumPriority medium-priority source topic
   * @param lowPriority low-priority source topic
   * @param partitions partition count shared by all six topics
   * @param replicationFactor replication factor shared by all six topics
   * @param minInSyncReplicas minimum in-sync replicas shared by all six topics
   * @param dltSuffix suffix used to derive the three dead-letter topic names
   */
  public record Topics(
      String highPriority,
      String mediumPriority,
      String lowPriority,
      int partitions,
      short replicationFactor,
      int minInSyncReplicas,
      String dltSuffix) {

    @ConstructorBinding
    public Topics {
      highPriority = requireText(highPriority, "notification.kafka.topics.high-priority");
      mediumPriority = requireText(mediumPriority, "notification.kafka.topics.medium-priority");
      lowPriority = requireText(lowPriority, "notification.kafka.topics.low-priority");
      requirePositive(partitions, "notification.kafka.topics.partitions");
      requirePositive(replicationFactor, "notification.kafka.topics.replication-factor");
      requirePositive(minInSyncReplicas, "notification.kafka.topics.min-in-sync-replicas");
      if (minInSyncReplicas > replicationFactor) {
        throw new IllegalStateException(
            "notification.kafka.topics.min-in-sync-replicas must not exceed "
                + "notification.kafka.topics.replication-factor");
      }
      dltSuffix = requireText(dltSuffix, "notification.kafka.topics.dlt-suffix");
    }

    public Topics(
        String highPriority,
        String mediumPriority,
        String lowPriority,
        int partitions,
        short replicationFactor,
        String dltSuffix) {
      this(highPriority, mediumPriority, lowPriority, partitions, replicationFactor,
          Math.min(2, replicationFactor), dltSuffix);
    }

    public Topics() {
      this(
          "high-priority-topic",
          "medium-priority-topic",
          "low-priority-topic",
          3,
          (short) 3,
          2,
          "-dlt");
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(NotificationKafkaProperties.class)
  static class Registration {
  }
}
