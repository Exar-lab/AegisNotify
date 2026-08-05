package com.aegisnotify.notification.infrastructure.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisnotify.notification.application.dto.TemplateRenderRequest;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.exception.TemplateRenderingException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MustacheTemplateRendererTest {

  private final MustacheTemplateRenderer renderer =
      new MustacheTemplateRenderer(500, Duration.ofHours(1));

  @Test
  void render_emailChannel_htmlEscapesValue() {
    TemplateRenderRequest request = new TemplateRenderRequest(
        "Hello {{name}}", Map.of("name", "Ada & Co."), List.of(), Channel.EMAIL);

    String result = renderer.render(request);

    assertEquals("Hello Ada &amp; Co.", result);
  }

  @ParameterizedTest
  @EnumSource(value = Channel.class, names = {"SMS", "WHATSAPP", "PUSH"})
  void render_nonEmailChannel_doesNotEscapeValue(Channel channel) {
    TemplateRenderRequest request = new TemplateRenderRequest(
        "Hello {{name}}", Map.of("name", "Ada & Co."), List.of(), channel);

    String result = renderer.render(request);

    assertEquals("Hello Ada & Co.", result);
  }

  @Test
  void render_singleMissingRequiredVariable_throwsNamingIt() {
    TemplateRenderRequest request = new TemplateRenderRequest(
        "Hello {{name}}, order {{orderId}}", Map.of("name", "Ada"),
        List.of("name", "orderId"), Channel.EMAIL);

    TemplateRenderingException exception = assertThrows(
        TemplateRenderingException.class, () -> renderer.render(request));

    assertTrue(exception.getMessage().contains("orderId"));
  }

  @Test
  void render_multipleMissingRequiredVariables_throwsNamingAllSorted() {
    TemplateRenderRequest request = new TemplateRenderRequest(
        "{{name}} {{orderId}} {{total}}", Map.of(),
        List.of("total", "name", "orderId"), Channel.EMAIL);

    TemplateRenderingException exception = assertThrows(
        TemplateRenderingException.class, () -> renderer.render(request));

    assertEquals("Missing required template variables: [name, orderId, total]",
        exception.getMessage());
  }

  @Test
  void render_nullParameterValue_treatedAsMissing() {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("name", null);
    TemplateRenderRequest request = new TemplateRenderRequest(
        "Hello {{name}}", parameters, List.of("name"), Channel.EMAIL);

    TemplateRenderingException exception = assertThrows(
        TemplateRenderingException.class, () -> renderer.render(request));

    assertTrue(exception.getMessage().contains("name"));
  }

  @Test
  void render_emptyRequiredVariables_skipsValidation() {
    TemplateRenderRequest request = new TemplateRenderRequest(
        "Hello {{name}}", Map.of(), List.of(), Channel.EMAIL);

    String result = renderer.render(request);

    assertEquals("Hello ", result);
  }

  @Test
  void render_malformedTemplateBody_wrapsIntoTemplateRenderingException() {
    TemplateRenderRequest request = new TemplateRenderRequest(
        "Hello {{#unclosedSection}}", Map.of(), List.of(), Channel.EMAIL);

    assertThrows(TemplateRenderingException.class, () -> renderer.render(request));
  }

  @Test
  void render_sameBodySameChannelTwice_reusesCompiledTemplate() {
    TemplateRenderRequest first = new TemplateRenderRequest(
        "Hello {{name}}", Map.of("name", "Ada"), List.of(), Channel.EMAIL);
    TemplateRenderRequest second = new TemplateRenderRequest(
        "Hello {{name}}", Map.of("name", "Bob"), List.of(), Channel.EMAIL);

    renderer.render(first);
    long loadCountAfterFirst = renderer.cacheLoadCount();
    renderer.render(second);
    long loadCountAfterSecond = renderer.cacheLoadCount();

    assertEquals(1, loadCountAfterFirst);
    assertEquals(loadCountAfterFirst, loadCountAfterSecond);
  }

  @Test
  void render_sameBodyDifferentChannel_compilesSeparately() {
    TemplateRenderRequest emailRequest = new TemplateRenderRequest(
        "Hello {{name}}", Map.of("name", "Ada"), List.of(), Channel.EMAIL);
    TemplateRenderRequest smsRequest = new TemplateRenderRequest(
        "Hello {{name}}", Map.of("name", "Ada"), List.of(), Channel.SMS);

    renderer.render(emailRequest);
    long loadCountAfterEmail = renderer.cacheLoadCount();
    renderer.render(smsRequest);
    long loadCountAfterSms = renderer.cacheLoadCount();

    assertEquals(1, loadCountAfterEmail);
    assertEquals(2, loadCountAfterSms);
  }
}
