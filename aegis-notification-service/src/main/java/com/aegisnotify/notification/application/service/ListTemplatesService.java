package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.TemplateResponse;
import com.aegisnotify.notification.application.port.in.ListTemplatesUseCase;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListTemplatesService implements ListTemplatesUseCase {

  private final TemplateRepository templateRepository;

  public ListTemplatesService(TemplateRepository templateRepository) {
    this.templateRepository = templateRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<TemplateResponse> list() {
    return templateRepository.findAll().stream()
        .map(TemplateResponse::from)
        .toList();
  }
}
