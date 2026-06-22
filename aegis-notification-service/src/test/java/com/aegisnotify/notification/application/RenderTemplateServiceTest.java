package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.RenderTemplateCommand;
import com.aegisnotify.notification.application.dto.RenderedTemplateResponse;
import com.aegisnotify.notification.application.port.out.TemplateRenderer;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.application.service.RenderTemplateService;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import com.aegisnotify.notification.domain.model.Template;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RenderTemplateServiceTest {

  @Mock
  private TemplateRepository templateRepository;

  @Mock
  private TemplateRenderer templateRenderer;

  @InjectMocks
  private RenderTemplateService service;

  @Test
  void render_templateFound_returnsRenderedResponse() {
    Instant now = Instant.now();
    Template template = Template.reconstitute(
        UUID.randomUUID(), "welcome", Channel.EMAIL, "Welcome!",
        "Hello {{name}}", List.of("name"), true, now, now
    );
    Map<String, Object> parameters = Map.of("name", "John");

    when(templateRepository.findActiveByName("welcome"))
        .thenReturn(Optional.of(template));
    when(templateRenderer.render("Hello {{name}}", parameters))
        .thenReturn("Hello John");

    RenderTemplateCommand command = new RenderTemplateCommand("welcome", parameters);
    RenderedTemplateResponse response = service.render(command);

    assertEquals("welcome", response.templateName());
    assertEquals("Welcome!", response.subject());
    assertEquals("Hello John", response.renderedBody());
  }

  @Test
  void render_templateNotFound_throwsTemplateNotFoundException() {
    RenderTemplateCommand command = new RenderTemplateCommand("nonexistent", Map.of());

    when(templateRepository.findActiveByName("nonexistent"))
        .thenReturn(Optional.empty());

    assertThrows(TemplateNotFoundException.class, () -> service.render(command));
  }
}
