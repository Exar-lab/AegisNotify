package com.aegisnotify.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.model.Template;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemplateTest {

  private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

  private Template existing(boolean active) {
    return Template.reconstitute(UUID.randomUUID(), "welcome", Channel.EMAIL, "Hi",
        "Hello {{name}}", List.of("name"), active, CREATED, CREATED);
  }

  @Test
  void create_generatesIdStartsActiveAndStampsTimestamps() {
    Template template = Template.create("welcome", Channel.SMS, null, "Hi {{name}}",
        List.of("name"));

    assertThat(template.getId()).isNotNull();
    assertThat(template.isActive()).isTrue();
    assertThat(template.getCreatedAt()).isNotNull().isEqualTo(template.getUpdatedAt());
    assertThat(template.getName()).isEqualTo("welcome");
    assertThat(template.getChannel()).isEqualTo(Channel.SMS);
    assertThat(template.getSubject()).isNull();
    assertThat(template.getVariables()).containsExactly("name");
  }

  @Test
  void create_nullVariables_defaultsToEmpty() {
    Template template = Template.create("welcome", Channel.SMS, null, "Hi", null);

    assertThat(template.getVariables()).isEmpty();
  }

  @Test
  void withContent_replacesContentKeepsIdentityAndBumpsUpdatedAt() {
    Template original = existing(true);

    Template updated = original.withContent("New", "Bye {{who}}", List.of("who"));

    assertThat(updated.getId()).isEqualTo(original.getId());
    assertThat(updated.getName()).isEqualTo("welcome");
    assertThat(updated.getChannel()).isEqualTo(Channel.EMAIL);
    assertThat(updated.isActive()).isTrue();
    assertThat(updated.getSubject()).isEqualTo("New");
    assertThat(updated.getBody()).isEqualTo("Bye {{who}}");
    assertThat(updated.getVariables()).containsExactly("who");
    assertThat(updated.getCreatedAt()).isEqualTo(CREATED);
    assertThat(updated.getUpdatedAt()).isAfter(CREATED);
    assertThat(original.getBody()).isEqualTo("Hello {{name}}");
  }

  @Test
  void withActive_changedState_returnsCopyWithBumpedUpdatedAt() {
    Template original = existing(true);

    Template deactivated = original.withActive(false);

    assertThat(deactivated.isActive()).isFalse();
    assertThat(deactivated.getUpdatedAt()).isAfter(CREATED);
    assertThat(original.isActive()).isTrue();
  }

  @Test
  void withActive_sameState_returnsSameInstance() {
    Template original = existing(false);

    assertThat(original.withActive(false)).isSameAs(original);
  }
}
