package com.aegisnotify.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.ProviderResult;
import com.aegisnotify.notification.domain.enums.Channel;
import org.junit.jupiter.api.Test;

class NotificationProviderRouterTest {

  private final SendGridEmailProviderAdapter emailAdapter =
      mock(SendGridEmailProviderAdapter.class);
  private final TwilioSmsProviderAdapter smsAdapter = mock(TwilioSmsProviderAdapter.class);
  private final TwilioWhatsAppProviderAdapter whatsAppAdapter =
      mock(TwilioWhatsAppProviderAdapter.class);
  private final FirebasePushProviderAdapter pushAdapter = mock(FirebasePushProviderAdapter.class);

  private final NotificationProviderRouter router = new NotificationProviderRouter(
      emailAdapter, smsAdapter, whatsAppAdapter, pushAdapter);

  @Test
  void dispatchesEmailChannelToSendGridAdapter() {
    ProviderResult expected = new ProviderResult(ProviderResult.Outcome.SENT, "SendGrid", null);
    when(emailAdapter.send(Channel.EMAIL, "to", "body", "subject")).thenReturn(expected);

    ProviderResult result = router.send(Channel.EMAIL, "to", "body", "subject");

    assertThat(result).isEqualTo(expected);
    verify(emailAdapter).send(Channel.EMAIL, "to", "body", "subject");
  }

  @Test
  void dispatchesSmsChannelToTwilioSmsAdapter() {
    ProviderResult expected = new ProviderResult(ProviderResult.Outcome.SENT, "Twilio", null);
    when(smsAdapter.send(Channel.SMS, "to", "body", "subject")).thenReturn(expected);

    ProviderResult result = router.send(Channel.SMS, "to", "body", "subject");

    assertThat(result).isEqualTo(expected);
    verify(smsAdapter).send(Channel.SMS, "to", "body", "subject");
  }

  @Test
  void dispatchesWhatsAppChannelToTwilioWhatsAppAdapter() {
    ProviderResult expected = new ProviderResult(ProviderResult.Outcome.SENT, "Twilio", null);
    when(whatsAppAdapter.send(Channel.WHATSAPP, "to", "body", "subject")).thenReturn(expected);

    ProviderResult result = router.send(Channel.WHATSAPP, "to", "body", "subject");

    assertThat(result).isEqualTo(expected);
    verify(whatsAppAdapter).send(Channel.WHATSAPP, "to", "body", "subject");
  }

  @Test
  void dispatchesPushChannelToFirebasePushAdapter() {
    ProviderResult expected = new ProviderResult(ProviderResult.Outcome.SENT, "FCM", null);
    when(pushAdapter.send(Channel.PUSH, "to", "body", "subject")).thenReturn(expected);

    ProviderResult result = router.send(Channel.PUSH, "to", "body", "subject");

    assertThat(result).isEqualTo(expected);
    verify(pushAdapter).send(Channel.PUSH, "to", "body", "subject");
  }
}
