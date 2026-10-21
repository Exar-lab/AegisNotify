export type MetricsChannel = 'EMAIL' | 'SMS' | 'WHATSAPP' | 'PUSH';

export interface ChannelRequestCount {
  channel: MetricsChannel;
  count: number;
}

export interface DeliveryLatency {
  count: number;
  meanSeconds: number;
  maxSeconds: number;
}

export interface ProviderErrorCount {
  provider: string;
  errorCode: string;
  count: number;
}

export interface KafkaConsumerMetrics {
  success: number;
  failure: number;
  retry: number;
  dlq: number;
}

export interface MetricsSummary {
  requestsByChannel: ChannelRequestCount[];
  deliveryLatency: DeliveryLatency;
  providerErrors: ProviderErrorCount[];
  fallbackTransmissions: number;
  kafkaConsumer: KafkaConsumerMetrics;
}
