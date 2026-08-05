package com.aegisnotify.notification.infrastructure.config;

import com.aegisnotify.notification.application.dto.NotificationEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer configuration for notification event ingestion.
 *
 * <p>Only active when {@code notification.kafka.consumer.enabled} is {@code true},
 * matching {@link NotificationKafkaProperties.Consumer#enabled()}. Dead letters route
 * to the six-topic topology's DLT names via {@link NotificationKafkaProperties#dltTopicFor}
 * rather than Spring Kafka's default {@code topic.DLT} convention.
 */
@Configuration
@ConditionalOnProperty(prefix = "notification.kafka.consumer", name = "enabled",
    havingValue = "true")
public class KafkaConsumerConfig {

  private static final long BACKOFF_INTERVAL_MS = 1000L;
  private static final long MAX_RETRIES = 2L;
  private static final String TRUSTED_PACKAGE =
      "com.aegisnotify.notification.application.dto";

  private final String bootstrapServers;
  private final NotificationKafkaProperties properties;

  public KafkaConsumerConfig(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
      NotificationKafkaProperties properties) {
    this.bootstrapServers = bootstrapServers;
    this.properties = properties;
  }

  @Bean
  public ConsumerFactory<String, NotificationEvent> notificationConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, properties.consumer().groupId());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
    props.put(JsonDeserializer.TRUSTED_PACKAGES, TRUSTED_PACKAGE);
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, NotificationEvent.class.getName());
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public CommonErrorHandler notificationErrorHandler(
      KafkaTemplate<String, Object> dltKafkaTemplate,
      NotificationKafkaMetrics metrics) {
    DeadLetterPublishingRecoverer dltRecoverer = new DeadLetterPublishingRecoverer(
        dltKafkaTemplate,
        (record, ex) -> new TopicPartition(
            properties.dltTopicFor(record.topic()), record.partition()));

    ConsumerRecordRecoverer recoverer = (record, ex) -> {
      metrics.recordFailure(record.topic());
      dltRecoverer.accept(record, ex);
      metrics.recordDlq(record.topic());
    };

    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer,
        new FixedBackOff(BACKOFF_INTERVAL_MS, MAX_RETRIES));
    errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
        metrics.recordRetry(record.topic()));
    return errorHandler;
  }

  @Bean
  public KafkaTemplate<String, Object> dltKafkaTemplate() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
    ProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(props);
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent>
      notificationKafkaListenerContainerFactory(
          ConsumerFactory<String, NotificationEvent> notificationConsumerFactory,
          CommonErrorHandler notificationErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(notificationConsumerFactory);
    factory.setCommonErrorHandler(notificationErrorHandler);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    return factory;
  }
}
