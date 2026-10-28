package com.aegisnotify.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.CreateTemplateCommand;
import com.aegisnotify.notification.application.dto.TemplateResponse;
import com.aegisnotify.notification.application.dto.UpdateTemplateCommand;
import com.aegisnotify.notification.application.port.out.TemplateRepository;
import com.aegisnotify.notification.application.service.CreateTemplateService;
import com.aegisnotify.notification.application.service.GetTemplateService;
import com.aegisnotify.notification.application.service.ListTemplatesService;
import com.aegisnotify.notification.application.service.SetTemplateStatusService;
import com.aegisnotify.notification.application.service.UpdateTemplateService;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.exception.DuplicateTemplateNameException;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import com.aegisnotify.notification.domain.model.Template;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemplateUseCaseServicesTest {

  private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

  @Mock
  private TemplateRepository templateRepository;

  private static Template template(UUID id, boolean active) {
    return Template.reconstitute(id, "welcome", Channel.EMAIL, "Hi",
        "Hello {{name}}", List.of("name"), active, CREATED, CREATED);
  }

  @Nested
  class ListTemplates {

    @Test
    void list_mapsEveryTemplateFromRepository() {
      UUID first = UUID.randomUUID();
      UUID second = UUID.randomUUID();
      when(templateRepository.findAll())
          .thenReturn(List.of(template(first, true), template(second, false)));

      List<TemplateResponse> result = new ListTemplatesService(templateRepository).list();

      assertThat(result).extracting(TemplateResponse::id).containsExactly(first, second);
      assertThat(result).extracting(TemplateResponse::active).containsExactly(true, false);
    }
  }

  @Nested
  class GetTemplate {

    @Test
    void get_existingId_returnsTemplate() {
      UUID id = UUID.randomUUID();
      when(templateRepository.findById(id)).thenReturn(Optional.of(template(id, true)));

      TemplateResponse result = new GetTemplateService(templateRepository).get(id);

      assertThat(result.id()).isEqualTo(id);
      assertThat(result.name()).isEqualTo("welcome");
      assertThat(result.channel()).isEqualTo(Channel.EMAIL);
      assertThat(result.variables()).containsExactly("name");
    }

    @Test
    void get_unknownId_throwsNotFound() {
      UUID id = UUID.randomUUID();
      when(templateRepository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> new GetTemplateService(templateRepository).get(id))
          .isInstanceOf(TemplateNotFoundException.class)
          .hasMessageContaining(id.toString());
    }
  }

  @Nested
  class CreateTemplate {

    @Test
    void create_newName_savesActiveTemplate() {
      when(templateRepository.existsByName("welcome")).thenReturn(false);
      when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));
      var command = new CreateTemplateCommand(
          "welcome", Channel.EMAIL, "Hi", "Hello {{name}}", List.of("name"));

      TemplateResponse result = new CreateTemplateService(templateRepository).create(command);

      ArgumentCaptor<Template> saved = ArgumentCaptor.forClass(Template.class);
      verify(templateRepository).save(saved.capture());
      assertThat(saved.getValue().isActive()).isTrue();
      assertThat(saved.getValue().getId()).isNotNull();
      assertThat(result.name()).isEqualTo("welcome");
      assertThat(result.active()).isTrue();
      assertThat(result.createdAt()).isEqualTo(result.updatedAt());
    }

    @Test
    void create_duplicateName_throwsConflictWithoutSaving() {
      when(templateRepository.existsByName("welcome")).thenReturn(true);
      var command = new CreateTemplateCommand(
          "welcome", Channel.EMAIL, null, "Hello", List.of());

      assertThatThrownBy(() -> new CreateTemplateService(templateRepository).create(command))
          .isInstanceOf(DuplicateTemplateNameException.class)
          .hasMessageContaining("welcome");
      verify(templateRepository, never()).save(any());
    }
  }

  @Nested
  class UpdateTemplate {

    @Test
    void update_existingId_replacesContentKeepsNameAndChannel() {
      UUID id = UUID.randomUUID();
      when(templateRepository.findById(id)).thenReturn(Optional.of(template(id, true)));
      when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));
      var command = new UpdateTemplateCommand("New", "Bye {{who}}", List.of("who"));

      TemplateResponse result = new UpdateTemplateService(templateRepository).update(id, command);

      assertThat(result.id()).isEqualTo(id);
      assertThat(result.name()).isEqualTo("welcome");
      assertThat(result.channel()).isEqualTo(Channel.EMAIL);
      assertThat(result.subject()).isEqualTo("New");
      assertThat(result.body()).isEqualTo("Bye {{who}}");
      assertThat(result.variables()).containsExactly("who");
      assertThat(result.updatedAt()).isAfter(CREATED);
    }

    @Test
    void update_unknownId_throwsNotFound() {
      UUID id = UUID.randomUUID();
      when(templateRepository.findById(id)).thenReturn(Optional.empty());
      var command = new UpdateTemplateCommand(null, "Body", List.of());

      assertThatThrownBy(() -> new UpdateTemplateService(templateRepository).update(id, command))
          .isInstanceOf(TemplateNotFoundException.class);
      verify(templateRepository, never()).save(any());
    }
  }

  @Nested
  class SetTemplateStatus {

    @Test
    void setStatus_changedState_savesAndReturnsNewState() {
      UUID id = UUID.randomUUID();
      when(templateRepository.findById(id)).thenReturn(Optional.of(template(id, true)));
      when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

      TemplateResponse result =
          new SetTemplateStatusService(templateRepository).setStatus(id, false);

      assertThat(result.active()).isFalse();
      verify(templateRepository).save(any(Template.class));
    }

    @Test
    void setStatus_sameState_isIdempotentAndDoesNotSave() {
      UUID id = UUID.randomUUID();
      when(templateRepository.findById(id)).thenReturn(Optional.of(template(id, true)));

      TemplateResponse result =
          new SetTemplateStatusService(templateRepository).setStatus(id, true);

      assertThat(result.active()).isTrue();
      assertThat(result.updatedAt()).isEqualTo(CREATED);
      verify(templateRepository, never()).save(any());
    }

    @Test
    void setStatus_unknownId_throwsNotFound() {
      UUID id = UUID.randomUUID();
      when(templateRepository.findById(id)).thenReturn(Optional.empty());

      assertThatThrownBy(
          () -> new SetTemplateStatusService(templateRepository).setStatus(id, true))
          .isInstanceOf(TemplateNotFoundException.class);
    }
  }
}
