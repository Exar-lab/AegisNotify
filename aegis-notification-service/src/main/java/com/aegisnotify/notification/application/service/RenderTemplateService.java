package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.RenderTemplateCommand;
import com.aegisnotify.notification.application.dto.RenderedTemplateResponse;
import com.aegisnotify.notification.application.port.in.RenderTemplateUseCase;
import com.aegisnotify.notification.application.port.out.TemplateRenderer;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import com.aegisnotify.notification.domain.model.Template;
import org.springframework.stereotype.Service;

@Service
public class RenderTemplateService implements RenderTemplateUseCase {

  private final TemplateRepository templateRepository;
  private final TemplateRenderer templateRenderer;

  public RenderTemplateService(TemplateRepository templateRepository,
      TemplateRenderer templateRenderer) {
    this.templateRepository = templateRepository;
    this.templateRenderer = templateRenderer;
  }

  @Override
  public RenderedTemplateResponse render(RenderTemplateCommand command) {
    Template template = templateRepository.findActiveByName(command.templateName())
        .orElseThrow(() -> new TemplateNotFoundException(command.templateName()));

    String renderedBody = templateRenderer.render(template.getBody(), command.parameters());

    return new RenderedTemplateResponse(
        template.getName(),
        template.getSubject(),
        renderedBody
    );
  }
}
