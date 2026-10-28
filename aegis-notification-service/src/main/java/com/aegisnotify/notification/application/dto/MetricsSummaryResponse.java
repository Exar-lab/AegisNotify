package com.aegisnotify.notification.application.dto;

import java.util.List;

/** Point-in-time JSON snapshot of the service's in-process operational metrics. */
public record MetricsSummaryResponse(
    List<ChannelRequestCount> requestsByChannel,
    DeliveryLatency deliveryLatency,
    List<ProviderErrorCount> providerErrors,
    long fallbackTransmissions,
    KafkaConsumerCounts kafkaConsumer
) {

  public record ChannelRequestCount(String channel, long count) {}

  public record DeliveryLatency(long count, double meanSeconds, double maxSeconds) {}

  public record ProviderErrorCount(String provider, String errorCode, long count) {}

  public record KafkaConsumerCounts(long success, long failure, long retry, long dlq) {}
}
