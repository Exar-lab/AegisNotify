package com.aegisnotify.notification.application.service;

import com.aegisnotify.notification.application.dto.CreateTemplateCommand;
import com.aegisnotify.notification.application.dto.TemplateResponse;
import com.aegisnotify.notification.application.port.in.CreateTemplateUseCase;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.domain.exception.DuplicateTemplateNameException;
import com.aegisnotify.notification.domain.model.Template;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a new, active template. Names are unique (case-sensitive exact
 * match); a taken name is rejected with {@link DuplicateTemplateNameException}.
 */
@Service
public class CreateTemplateService implements CreateTemplateUseCase {

  private final TemplateRepository templateRepository;

  public CreateTemplateService(TemplateRepository templateRepository) {
    this.templateRepository = templateRepository;
  }

  @Override
  @Transactional
  public TemplateResponse create(CreateTemplateCommand command) {
    if (templateRepository.existsByName(command.name())) {
      throw new DuplicateTemplateNameException(command.name());
    }
    Template template = Template.create(
        command.name(), command.channel(), command.subject(),
        command.body(), command.variables());
    return TemplateResponse.from(templateRepository.save(template));
  }
}
