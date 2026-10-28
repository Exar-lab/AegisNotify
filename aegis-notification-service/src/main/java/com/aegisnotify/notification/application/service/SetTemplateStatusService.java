package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.TemplateResponse;
import com.aegisnotify.notification.application.port.in.SetTemplateStatusUseCase;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import com.aegisnotify.notification.domain.model.Template;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activates or deactivates a template. Idempotent: requesting the current
 * state succeeds without writing anything.
 */
@Service
public class SetTemplateStatusService implements SetTemplateStatusUseCase {

  private final TemplateRepository templateRepository;

  public SetTemplateStatusService(TemplateRepository templateRepository) {
    this.templateRepository = templateRepository;
  }

  @Override
  @Transactional
  public TemplateResponse setStatus(UUID id, boolean active) {
    Template existing = templateRepository.findById(id)
        .orElseThrow(() -> new TemplateNotFoundException(id));
    if (existing.isActive() == active) {
      return TemplateResponse.from(existing);
    }
    return TemplateResponse.from(templateRepository.save(existing.withActive(active)));
  }
}
