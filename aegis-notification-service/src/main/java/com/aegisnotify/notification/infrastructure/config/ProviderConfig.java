package com.aegisnotify.notification.infrastructure.config;

import com.aegisnotify.notification.infrastructure.provider.FirebasePushProviderAdapter;
import com.aegisnotify.notification.infrastructure.provider.NotificationProviderRouter;
import com.aegisnotify.notification.infrastructure.provider.SendGridEmailProviderAdapter;
import com.aegisnotify.notification.infrastructure.provider.TwilioSmsProviderAdapter;
import com.aegisnotify.notification.infrastructure.provider.TwilioWhatsAppProviderAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wires the notification provider adapters (SendGrid, Twilio, FCM) as Spring
 * beans, each backed by its own {@link WebClient} pointed at the vendor's
 * base URL, plus the {@link NotificationProviderRouter} that dispatches
 * between them. Fails fast at startup if any required credential is blank,
 * matching the {@code AesGcmEncryptionAdapter} convention in
 * aegis-audit-service — a silently blank API key would otherwise only
 * surface as a per-notification 401 from the vendor in production.
 */
@Configuration
public class ProviderConfig {

  @Bean
  public SendGridEmailProviderAdapter sendGridEmailProviderAdapter(
      @Value("${notification.providers.email.base-url}") String baseUrl,
      @Value("${notification.providers.email.api-key}") String apiKey,
      @Value("${notification.providers.email.from-address}") String fromAddress) {
    requireNonBlank(apiKey, "notification.providers.email.api-key");
    return new SendGridEmailProviderAdapter(
        WebClient.builder().baseUrl(baseUrl).build(), apiKey, fromAddress);
  }

  @Bean
  public TwilioSmsProviderAdapter twilioSmsProviderAdapter(
      @Value("${notification.providers.sms.base-url}") String baseUrl,
      @Value("${notification.providers.sms.account-sid}") String accountSid,
      @Value("${notification.providers.sms.auth-token}") String authToken,
      @Value("${notification.providers.sms.from-number}") String fromNumber) {
    requireNonBlank(accountSid, "notification.providers.sms.account-sid");
    requireNonBlank(authToken, "notification.providers.sms.auth-token");
    return new TwilioSmsProviderAdapter(
        buildBasicAuthWebClient(baseUrl, accountSid, authToken), accountSid, fromNumber);
  }

  @Bean
  public TwilioWhatsAppProviderAdapter twilioWhatsAppProviderAdapter(
      @Value("${notification.providers.whatsapp.base-url}") String baseUrl,
      @Value("${notification.providers.whatsapp.account-sid}") String accountSid,
      @Value("${notification.providers.whatsapp.auth-token}") String authToken,
      @Value("${notification.providers.whatsapp.from-number}") String fromNumber) {
    requireNonBlank(accountSid, "notification.providers.whatsapp.account-sid");
    requireNonBlank(authToken, "notification.providers.whatsapp.auth-token");
    return new TwilioWhatsAppProviderAdapter(
        buildBasicAuthWebClient(baseUrl, accountSid, authToken), accountSid, fromNumber);
  }

  @Bean
  public FirebasePushProviderAdapter firebasePushProviderAdapter(
      @Value("${notification.providers.push.base-url}") String baseUrl,
      @Value("${notification.providers.push.project-id}") String projectId,
      @Value("${notification.providers.push.access-token}") String accessToken) {
    requireNonBlank(projectId, "notification.providers.push.project-id");
    requireNonBlank(accessToken, "notification.providers.push.access-token");
    return new FirebasePushProviderAdapter(
        WebClient.builder().baseUrl(baseUrl).build(), projectId, accessToken);
  }

  @Bean
  public NotificationProviderRouter notificationProviderRouter(
      SendGridEmailProviderAdapter sendGridEmailProviderAdapter,
      TwilioSmsProviderAdapter twilioSmsProviderAdapter,
      TwilioWhatsAppProviderAdapter twilioWhatsAppProviderAdapter,
      FirebasePushProviderAdapter firebasePushProviderAdapter) {
    return new NotificationProviderRouter(
        sendGridEmailProviderAdapter, twilioSmsProviderAdapter,
        twilioWhatsAppProviderAdapter, firebasePushProviderAdapter);
  }

  private WebClient buildBasicAuthWebClient(String baseUrl, String accountSid,
      String authToken) {
    return WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeaders(headers -> headers.setBasicAuth(accountSid, authToken))
        .build();
  }

  private void requireNonBlank(String value, String propertyName) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(propertyName + " must be configured");
    }
  }
}
