package com.aegisnotify.notification.application.port.out;

import com.aegisnotify.notification.domain.model.Template;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateRepository {

  Optional<Template> findActiveByName(String name);

  /**
   * Returns every template (active and inactive), ordered by name.
   *
   * @return all templates
   */
  List<Template> findAll();

  Optional<Template> findById(UUID id);

  /**
   * Case-sensitive exact-match name lookup, regardless of active state.
   *
   * @param name the template name
   * @return {@code true} if a template with that name exists
   */
  boolean existsByName(String name);

  Template save(Template template);
}
