package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.TemplateResponse;
import com.aegisnotify.notification.application.dto.UpdateTemplateCommand;
import com.aegisnotify.notification.application.port.in.UpdateTemplateUseCase;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import com.aegisnotify.notification.domain.model.Template;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces the editable content of a template ({@code subject},
 * {@code body}, {@code variables}). {@code name} and {@code channel} never
 * change here.
 */
@Service
public class UpdateTemplateService implements UpdateTemplateUseCase {

  private final TemplateRepository templateRepository;

  public UpdateTemplateService(TemplateRepository templateRepository) {
    this.templateRepository = templateRepository;
  }

  @Override
  @Transactional
  public TemplateResponse update(UUID id, UpdateTemplateCommand command) {
    Template existing = templateRepository.findById(id)
        .orElseThrow(() -> new TemplateNotFoundException(id));
    Template updated = existing.withContent(
        command.subject(), command.body(), command.variables());
    return TemplateResponse.from(templateRepository.save(updated));
  }
}
