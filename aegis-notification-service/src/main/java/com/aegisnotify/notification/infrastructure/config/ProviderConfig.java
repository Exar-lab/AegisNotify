package com.aegisnotify.notification.infrastructure.config;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.application.port.out.NotificationProviderPort;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.infrastructure.provider.FirebasePushProviderAdapter;
import com.aegisnotify.notification.infrastructure.provider.NotificationProviderRouter;
import com.aegisnotify.notification.infrastructure.provider.ResilientNotificationProviderAdapter;
import com.aegisnotify.notification.infrastructure.provider.SendGridEmailProviderAdapter;
import com.aegisnotify.notification.infrastructure.provider.TwilioSmsProviderAdapter;
import com.aegisnotify.notification.infrastructure.provider.TwilioWhatsAppProviderAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wires the notification provider adapters (SendGrid, Twilio, FCM) as Spring
 * beans, each backed by its own {@link WebClient} pointed at the vendor's
 * base URL, plus the {@link ResilientNotificationProviderAdapter} that
 * circuit-breaks and fails over between a primary and secondary account per
 * channel. Primary credentials fail fast at startup if blank, matching the
 * {@code AesGcmEncryptionAdapter} convention in aegis-audit-service — a
 * silently blank API key would otherwise only surface as a per-notification
 * 401 from the vendor in production. Secondary credentials are optional: an
 * unconfigured secondary degrades to always reporting delivery failure,
 * which correctly routes to {@code FAILED_CRITICAL} rather than blocking
 * startup for deployments with no secondary account provisioned yet.
 */
@Configuration
public class ProviderConfig {

  private static final NotificationProviderPort NO_SECONDARY_PROVIDER =
      (channel, recipient, renderedContent, subject) -> new ProviderResult(
          ProviderResult.Outcome.FAILED, "none",
          "No secondary provider configured for channel " + channel);

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

  @Bean
  public Map<Channel, NotificationProviderPort> secondaryProvidersByChannel(
      @Value("${notification.providers.email.secondary.base-url}") String emailBaseUrl,
      @Value("${notification.providers.email.secondary.api-key}") String emailApiKey,
      @Value("${notification.providers.email.secondary.from-address}") String emailFromAddress,
      @Value("${notification.providers.sms.secondary.base-url}") String smsBaseUrl,
      @Value("${notification.providers.sms.secondary.account-sid}") String smsAccountSid,
      @Value("${notification.providers.sms.secondary.auth-token}") String smsAuthToken,
      @Value("${notification.providers.sms.secondary.from-number}") String smsFromNumber,
      @Value("${notification.providers.whatsapp.secondary.base-url}") String whatsAppBaseUrl,
      @Value("${notification.providers.whatsapp.secondary.account-sid}") String whatsAppAccountSid,
      @Value("${notification.providers.whatsapp.secondary.auth-token}") String whatsAppAuthToken,
      @Value("${notification.providers.whatsapp.secondary.from-number}") String whatsAppFromNumber,
      @Value("${notification.providers.push.secondary.base-url}") String pushBaseUrl,
      @Value("${notification.providers.push.secondary.project-id}") String pushProjectId,
      @Value("${notification.providers.push.secondary.access-token}") String pushAccessToken) {
    Map<Channel, NotificationProviderPort> secondary = new EnumMap<>(Channel.class);
    secondary.put(Channel.EMAIL,
        secondaryEmail(emailBaseUrl, emailApiKey, emailFromAddress));
    secondary.put(Channel.SMS,
        secondarySms(smsBaseUrl, smsAccountSid, smsAuthToken, smsFromNumber));
    secondary.put(Channel.WHATSAPP,
        secondaryWhatsApp(whatsAppBaseUrl, whatsAppAccountSid, whatsAppAuthToken,
            whatsAppFromNumber));
    secondary.put(Channel.PUSH,
        secondaryPush(pushBaseUrl, pushProjectId, pushAccessToken));
    return Map.copyOf(secondary);
  }

  @Bean
  public ResilientNotificationProviderAdapter resilientNotificationProviderAdapter(
      NotificationProviderRouter notificationProviderRouter,
      Map<Channel, NotificationProviderPort> secondaryProvidersByChannel,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new ResilientNotificationProviderAdapter(
        notificationProviderRouter, secondaryProvidersByChannel, circuitBreakerRegistry);
  }

  private NotificationProviderPort secondaryEmail(String baseUrl, String apiKey,
      String fromAddress) {
    if (apiKey == null || apiKey.isBlank()) {
      return NO_SECONDARY_PROVIDER;
    }
    SendGridEmailProviderAdapter adapter = new SendGridEmailProviderAdapter(
        WebClient.builder().baseUrl(baseUrl).build(), apiKey, fromAddress);
    return adapter::send;
  }

  private NotificationProviderPort secondarySms(String baseUrl, String accountSid,
      String authToken, String fromNumber) {
    if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank()) {
      return NO_SECONDARY_PROVIDER;
    }
    TwilioSmsProviderAdapter adapter = new TwilioSmsProviderAdapter(
        buildBasicAuthWebClient(baseUrl, accountSid, authToken), accountSid, fromNumber);
    return adapter::send;
  }

  private NotificationProviderPort secondaryWhatsApp(String baseUrl, String accountSid,
      String authToken, String fromNumber) {
    if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank()) {
      return NO_SECONDARY_PROVIDER;
    }
    TwilioWhatsAppProviderAdapter adapter = new TwilioWhatsAppProviderAdapter(
        buildBasicAuthWebClient(baseUrl, accountSid, authToken), accountSid, fromNumber);
    return adapter::send;
  }

  private NotificationProviderPort secondaryPush(String baseUrl, String projectId,
      String accessToken) {
    if (projectId == null || projectId.isBlank() || accessToken == null
        || accessToken.isBlank()) {
      return NO_SECONDARY_PROVIDER;
    }
    FirebasePushProviderAdapter adapter = new FirebasePushProviderAdapter(
        WebClient.builder().baseUrl(baseUrl).build(), projectId, accessToken);
    return adapter::send;
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
