package com.aegisnotify.notification.infrastructure.template;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheException;
import java.io.IOException;
import java.io.Writer;

/**
 * Writes substituted values verbatim, with no HTML entity encoding, for
 * channels where the rendered body is not interpreted as HTML (SMS,
 * WhatsApp, Push).
 */
final class PlainTextMustacheFactory extends DefaultMustacheFactory {

  @Override
  public void encode(String value, Writer writer) {
    try {
      writer.write(value);
    } catch (IOException e) {
      throw new MustacheException("Failed to write template value", e);
    }
  }
}
