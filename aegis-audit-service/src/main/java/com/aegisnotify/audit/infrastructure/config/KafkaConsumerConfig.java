package com.aegisnotify.audit.infrastructure.config;

import com.aegisnotify.audit.application.dto.AuditEventCommand;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer configuration for audit event ingestion.
 */
@Configuration
public class KafkaConsumerConfig {

  private static final long BACKOFF_INTERVAL_MS = 1000L;
  private static final long MAX_ATTEMPTS = 3L;
  private static final String TRUSTED_PACKAGE =
      "com.aegisnotify.audit.application.dto";

  private final String bootstrapServers;
  private final String groupId;

  public KafkaConsumerConfig(
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
      String bootstrapServers,
      @Value("${audit.consumer.group-id:audit-service}")
      String groupId) {
    this.bootstrapServers = bootstrapServers;
    this.groupId = groupId;
  }

  @Bean
  public ConsumerFactory<String, AuditEventCommand>
      auditConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        ErrorHandlingDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS,
        StringDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS,
        JsonDeserializer.class);
    props.put(JsonDeserializer.TRUSTED_PACKAGES, TRUSTED_PACKAGE);
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
        AuditEventCommand.class.getName());
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public CommonErrorHandler auditErrorHandler(
      KafkaTemplate<String, Object> dltKafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(dltKafkaTemplate);
    return new DefaultErrorHandler(recoverer,
        new FixedBackOff(BACKOFF_INTERVAL_MS, MAX_ATTEMPTS));
  }

  @Bean
  public KafkaTemplate<String, Object> dltKafkaTemplate() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        JsonSerializer.class);
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, AuditEventCommand>
      auditKafkaListenerContainerFactory(
          ConsumerFactory<String, AuditEventCommand> auditConsumerFactory,
          CommonErrorHandler auditErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, AuditEventCommand>
        factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(auditConsumerFactory);
    factory.setCommonErrorHandler(auditErrorHandler);
    factory.getContainerProperties()
        .setAckMode(ContainerProperties.AckMode.MANUAL);
    return factory;
  }
}
