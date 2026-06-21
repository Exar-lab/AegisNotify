package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.domain.model.Template;
import java.util.Optional;

public interface TemplateRepository {

  Optional<Template> findActiveByName(String name);
}
