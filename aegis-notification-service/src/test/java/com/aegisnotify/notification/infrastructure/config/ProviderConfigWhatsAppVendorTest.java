package com.aegisnotify.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

import com.aegisnotify.notification.infrastructure.provider.TwilioWhatsAppProviderAdapter;
import com.aegisnotify.notification.infrastructure.provider.VonageWhatsAppProviderAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProviderConfigWhatsAppVendorTest {

  private static final String SANDBOX_URL = "https://messages-sandbox.nexmo.com";
  private static final String TWILIO_URL = "https://api.twilio.com";

  private final ProviderConfig config = new ProviderConfig();
  private final TwilioWhatsAppProviderAdapter twilio = mock(TwilioWhatsAppProviderAdapter.class);
  private final VonageWhatsAppProviderAdapter vonage = mock(VonageWhatsAppProviderAdapter.class);

  @ParameterizedTest
  @ValueSource(strings = {"vonage", "VONAGE"})
  void primaryWhatsAppProviderSelectsVonageWhenConfigured(String vendor) {
    assertThat(config.primaryWhatsAppProvider(vendor, twilio, vonage)).isSameAs(vonage);
  }

  @ParameterizedTest
  @ValueSource(strings = {"twilio", "TWILIO"})
  void primaryWhatsAppProviderSelectsTwilioByDefault(String vendor) {
    assertThat(config.primaryWhatsAppProvider(vendor, twilio, vonage)).isSameAs(twilio);
  }

  @Test
  void vonageAdapterRequiresCredentialsWhenSelected() {
    assertThatIllegalStateException().isThrownBy(() -> config.vonageWhatsAppProviderAdapter(
            "vonage", SANDBOX_URL, "", "secret", "14157386102", new SimpleMeterRegistry()))
        .withMessageContaining("notification.providers.whatsapp.vonage.api-key");
    assertThatIllegalStateException().isThrownBy(() -> config.vonageWhatsAppProviderAdapter(
            "vonage", SANDBOX_URL, "key", " ", "14157386102", new SimpleMeterRegistry()))
        .withMessageContaining("notification.providers.whatsapp.vonage.api-secret");
    assertThatIllegalStateException().isThrownBy(() -> config.vonageWhatsAppProviderAdapter(
            "vonage", SANDBOX_URL, "key", "secret", "", new SimpleMeterRegistry()))
        .withMessageContaining("notification.providers.whatsapp.vonage.from-number");
  }

  @Test
  void vonageAdapterSkipsValidationWhenTwilioIsSelected() {
    assertThatNoException().isThrownBy(() -> config.vonageWhatsAppProviderAdapter(
        "twilio", SANDBOX_URL, "", "", "", new SimpleMeterRegistry()));
  }

  @Test
  void twilioAdapterSkipsValidationWhenVonageIsSelected() {
    assertThatNoException().isThrownBy(() -> config.twilioWhatsAppProviderAdapter(
        "vonage", TWILIO_URL, "", "", "", "", new SimpleMeterRegistry()));
  }

  @Test
  void twilioAdapterRequiresCredentialsWhenSelected() {
    assertThatIllegalStateException().isThrownBy(() -> config.twilioWhatsAppProviderAdapter(
            "twilio", TWILIO_URL, "", "token", "+14155238886", "", new SimpleMeterRegistry()))
        .withMessageContaining("notification.providers.whatsapp.account-sid");
  }
}
