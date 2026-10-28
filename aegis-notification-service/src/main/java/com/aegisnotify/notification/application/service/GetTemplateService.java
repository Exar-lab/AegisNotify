package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.TemplateResponse;
import com.aegisnotify.notification.application.port.in.GetTemplateUseCase;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetTemplateService implements GetTemplateUseCase {

  private final TemplateRepository templateRepository;

  public GetTemplateService(TemplateRepository templateRepository) {
    this.templateRepository = templateRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public TemplateResponse get(UUID id) {
    return templateRepository.findById(id)
        .map(TemplateResponse::from)
        .orElseThrow(() -> new TemplateNotFoundException(id));
  }
}
