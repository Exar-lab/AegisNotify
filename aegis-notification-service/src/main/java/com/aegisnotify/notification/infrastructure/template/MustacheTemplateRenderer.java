package com.aegisnotify.notification.infrastructure.template;

import com.aegisnotify.notification.application.dto.TemplateRenderRequest;
import com.aegisnotify.notification.application.port.out.TemplateRenderer;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.exception.TemplateRenderingException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
import com.github.mustachejava.MustacheFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Renders Mustache templates, HTML-escaping substituted values for
 * {@link Channel#EMAIL} and writing them verbatim for every other channel.
 * Compiled templates are cached, content-addressed by escape mode and body,
 * since a compiled {@link Mustache} is permanently bound to the factory
 * (and therefore the escaping policy) that compiled it.
 */
@Component
public class MustacheTemplateRenderer implements TemplateRenderer {

  private final MustacheFactory htmlEscapingFactory = new DefaultMustacheFactory();
  private final MustacheFactory plainTextFactory = new PlainTextMustacheFactory();
  private final Cache<CacheKey, Mustache> compiledTemplates;

  public MustacheTemplateRenderer(
      @Value("${notification.template.cache.maximum-size:500}") long maximumSize,
      @Value("${notification.template.cache.expire-after-write:PT1H}") Duration expireAfterWrite) {
    this.compiledTemplates = Caffeine.newBuilder()
        .maximumSize(maximumSize)
        .expireAfterWrite(expireAfterWrite)
        .recordStats()
        .build();
  }

  @Override
  public String render(TemplateRenderRequest request) {
    validateRequiredVariables(request);

    EscapeMode mode = escapeModeFor(request.channel());
    CacheKey key = new CacheKey(mode, request.templateBody());
    Mustache mustache = compiledTemplates.get(key, this::compile);

    StringWriter writer = new StringWriter();
    try {
      mustache.execute(writer, request.parameters());
    } catch (MustacheException e) {
      throw new TemplateRenderingException("Failed to render template", e);
    }
    return writer.toString();
  }

  private void validateRequiredVariables(TemplateRenderRequest request) {
    List<String> missing = request.requiredVariables().stream()
        .filter(name -> !request.parameters().containsKey(name)
            || request.parameters().get(name) == null)
        .sorted()
        .toList();
    if (!missing.isEmpty()) {
      throw new TemplateRenderingException("Missing required template variables: " + missing);
    }
  }

  private Mustache compile(CacheKey key) {
    MustacheFactory factory =
        key.mode() == EscapeMode.HTML ? htmlEscapingFactory : plainTextFactory;
    try {
      return factory.compile(new StringReader(key.templateBody()), UUID.randomUUID().toString());
    } catch (MustacheException e) {
      throw new TemplateRenderingException("Failed to compile template", e);
    }
  }

  private static EscapeMode escapeModeFor(Channel channel) {
    return channel == Channel.EMAIL ? EscapeMode.HTML : EscapeMode.PLAIN;
  }

  long cacheLoadCount() {
    return compiledTemplates.stats().loadCount();
  }

  private enum EscapeMode {
    HTML,
    PLAIN
  }

  private record CacheKey(EscapeMode mode, String templateBody) {
  }
}
