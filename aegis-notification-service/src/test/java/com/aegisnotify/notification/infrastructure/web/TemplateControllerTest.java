package com.aegisnotify.notification.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisnotify.notification.application.dto.CreateTemplateCommand;
import com.aegisnotify.notification.application.dto.RenderTemplateCommand;
import com.aegisnotify.notification.application.dto.RenderedTemplateResponse;
import com.aegisnotify.notification.application.dto.TemplateResponse;
import com.aegisnotify.notification.application.dto.UpdateTemplateCommand;
import com.aegisnotify.notification.application.port.in.CreateTemplateUseCase;
import com.aegisnotify.notification.application.port.in.GetTemplateUseCase;
import com.aegisnotify.notification.application.port.in.ListTemplatesUseCase;
import com.aegisnotify.notification.application.port.in.RenderTemplateUseCase;
import com.aegisnotify.notification.application.port.in.SetTemplateStatusUseCase;
import com.aegisnotify.notification.application.port.in.UpdateTemplateUseCase;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.exception.DuplicateTemplateNameException;
import com.aegisnotify.notification.domain.exception.TemplateNotFoundException;
import com.aegisnotify.notification.domain.exception.TemplateRenderingException;
import com.aegisnotify.notification.infrastructure.config.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TemplateController.class)
@Import(SecurityConfig.class)
class TemplateControllerTest {

  private static final String READ = "SCOPE_notification:read";
  private static final String WRITE = "SCOPE_notification:write";
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ListTemplatesUseCase listTemplatesUseCase;

  @MockitoBean
  private GetTemplateUseCase getTemplateUseCase;

  @MockitoBean
  private CreateTemplateUseCase createTemplateUseCase;

  @MockitoBean
  private UpdateTemplateUseCase updateTemplateUseCase;

  @MockitoBean
  private SetTemplateStatusUseCase setTemplateStatusUseCase;

  @MockitoBean
  private RenderTemplateUseCase renderTemplateUseCase;

  private static TemplateResponse response(UUID id, boolean active) {
    return new TemplateResponse(id, "welcome", Channel.EMAIL, "Hi", "Hello {{name}}",
        List.of("name"), active, NOW, NOW);
  }

  @Test
  void list_returns200WithAllTemplates() throws Exception {
    UUID id = UUID.randomUUID();
    when(listTemplatesUseCase.list()).thenReturn(List.of(response(id, true)));

    mockMvc.perform(get("/api/v1/templates").with(jwt().authorities(() -> READ)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id.toString()))
        .andExpect(jsonPath("$[0].name").value("welcome"))
        .andExpect(jsonPath("$[0].channel").value("EMAIL"))
        .andExpect(jsonPath("$[0].subject").value("Hi"))
        .andExpect(jsonPath("$[0].body").value("Hello {{name}}"))
        .andExpect(jsonPath("$[0].variables[0]").value("name"))
        .andExpect(jsonPath("$[0].active").value(true))
        .andExpect(jsonPath("$[0].createdAt").exists())
        .andExpect(jsonPath("$[0].updatedAt").exists());
  }

  @Test
  void list_withoutReadScope_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/templates").with(jwt().authorities(() -> WRITE)))
        .andExpect(status().isForbidden());
  }

  @Test
  void list_noToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/templates"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void get_existingId_returns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(getTemplateUseCase.get(id)).thenReturn(response(id, true));

    mockMvc.perform(get("/api/v1/templates/{id}", id).with(jwt().authorities(() -> READ)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()));
  }

  @Test
  void get_unknownId_returns404() throws Exception {
    UUID id = UUID.randomUUID();
    when(getTemplateUseCase.get(id)).thenThrow(new TemplateNotFoundException(id));

    mockMvc.perform(get("/api/v1/templates/{id}", id).with(jwt().authorities(() -> READ)))
        .andExpect(status().isNotFound());
  }

  @Test
  void create_validRequest_returns201() throws Exception {
    UUID id = UUID.randomUUID();
    var expected = new CreateTemplateCommand(
        "welcome", Channel.EMAIL, "Hi", "Hello {{name}}", List.of("name"));
    when(createTemplateUseCase.create(expected)).thenReturn(response(id, true));

    mockMvc.perform(post("/api/v1/templates")
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"welcome","channel":"EMAIL","subject":"Hi",
                 "body":"Hello {{name}}","variables":["name"]}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  void create_missingVariables_defaultsToEmptyList() throws Exception {
    var expected = new CreateTemplateCommand("sms", Channel.SMS, null, "Hi", List.of());
    when(createTemplateUseCase.create(expected)).thenReturn(response(UUID.randomUUID(), true));

    mockMvc.perform(post("/api/v1/templates")
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"sms\",\"channel\":\"SMS\",\"body\":\"Hi\"}"))
        .andExpect(status().isCreated());

    verify(createTemplateUseCase).create(expected);
  }

  @Test
  void create_duplicateName_returns409() throws Exception {
    when(createTemplateUseCase.create(any()))
        .thenThrow(new DuplicateTemplateNameException("welcome"));

    mockMvc.perform(post("/api/v1/templates")
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"welcome\",\"channel\":\"EMAIL\",\"body\":\"Hi\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  void create_blankName_returns400() throws Exception {
    mockMvc.perform(post("/api/v1/templates")
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\" \",\"channel\":\"EMAIL\",\"body\":\"Hi\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(createTemplateUseCase);
  }

  @Test
  void create_nameTooLong_returns400() throws Exception {
    String name = "n".repeat(121);
    mockMvc.perform(post("/api/v1/templates")
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + name + "\",\"channel\":\"EMAIL\",\"body\":\"Hi\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(createTemplateUseCase);
  }

  @Test
  void create_missingChannelOrBlankBody_returns400() throws Exception {
    mockMvc.perform(post("/api/v1/templates")
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"welcome\",\"body\":\"Hi\"}"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(post("/api/v1/templates")
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"welcome\",\"channel\":\"EMAIL\",\"body\":\"\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(createTemplateUseCase);
  }

  @Test
  void create_unknownChannel_returns400() throws Exception {
    mockMvc.perform(post("/api/v1/templates")
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"welcome\",\"channel\":\"FAX\",\"body\":\"Hi\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(createTemplateUseCase);
  }

  @Test
  void create_withoutWriteScope_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/templates")
            .with(jwt().authorities(() -> READ))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"welcome\",\"channel\":\"EMAIL\",\"body\":\"Hi\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void update_validRequest_returns200AndIgnoresNameAndChannel() throws Exception {
    UUID id = UUID.randomUUID();
    var expected = new UpdateTemplateCommand("New", "Bye", List.of());
    when(updateTemplateUseCase.update(id, expected)).thenReturn(response(id, true));

    mockMvc.perform(put("/api/v1/templates/{id}", id)
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"renamed\",\"channel\":\"SMS\","
                + "\"subject\":\"New\",\"body\":\"Bye\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("welcome"));

    verify(updateTemplateUseCase).update(id, expected);
  }

  @Test
  void update_unknownId_returns404() throws Exception {
    UUID id = UUID.randomUUID();
    when(updateTemplateUseCase.update(eq(id), any()))
        .thenThrow(new TemplateNotFoundException(id));

    mockMvc.perform(put("/api/v1/templates/{id}", id)
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":\"Bye\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void update_blankBody_returns400() throws Exception {
    mockMvc.perform(put("/api/v1/templates/{id}", UUID.randomUUID())
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":\"  \"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(updateTemplateUseCase);
  }

  @Test
  void update_withoutWriteScope_returns403() throws Exception {
    mockMvc.perform(put("/api/v1/templates/{id}", UUID.randomUUID())
            .with(jwt().authorities(() -> READ))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":\"Bye\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void setStatus_validRequest_returns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(setTemplateStatusUseCase.setStatus(id, false)).thenReturn(response(id, false));

    mockMvc.perform(patch("/api/v1/templates/{id}/status", id)
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void setStatus_unknownId_returns404() throws Exception {
    UUID id = UUID.randomUUID();
    when(setTemplateStatusUseCase.setStatus(id, true))
        .thenThrow(new TemplateNotFoundException(id));

    mockMvc.perform(patch("/api/v1/templates/{id}/status", id)
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"active\":true}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void setStatus_missingActive_returns400() throws Exception {
    mockMvc.perform(patch("/api/v1/templates/{id}/status", UUID.randomUUID())
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(setTemplateStatusUseCase);
  }

  @Test
  void setStatus_withoutWriteScope_returns403() throws Exception {
    mockMvc.perform(patch("/api/v1/templates/{id}/status", UUID.randomUUID())
            .with(jwt().authorities(() -> READ))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"active\":true}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void render_validRequest_returns200WithSubjectAndBody() throws Exception {
    var expected = new RenderTemplateCommand("welcome", Map.of("name", "Ana"));
    when(renderTemplateUseCase.render(expected))
        .thenReturn(new RenderedTemplateResponse("welcome", "Hi", "Hello Ana"));

    mockMvc.perform(post("/api/v1/templates/render")
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"templateName\":\"welcome\",\"parameters\":{\"name\":\"Ana\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("welcome"))
        .andExpect(jsonPath("$.subject").value("Hi"))
        .andExpect(jsonPath("$.body").value("Hello Ana"));
  }

  @Test
  void render_missingParameters_defaultsToEmptyMap() throws Exception {
    var expected = new RenderTemplateCommand("welcome", Map.of());
    when(renderTemplateUseCase.render(expected))
        .thenReturn(new RenderedTemplateResponse("welcome", null, "Hello"));

    mockMvc.perform(post("/api/v1/templates/render")
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"templateName\":\"welcome\"}"))
        .andExpect(status().isOk());

    verify(renderTemplateUseCase).render(expected);
  }

  @Test
  void render_unknownTemplate_returns404() throws Exception {
    when(renderTemplateUseCase.render(any()))
        .thenThrow(new TemplateNotFoundException("ghost"));

    mockMvc.perform(post("/api/v1/templates/render")
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"templateName\":\"ghost\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void render_missingVariables_returns422() throws Exception {
    when(renderTemplateUseCase.render(any()))
        .thenThrow(new TemplateRenderingException("Missing required variables: [name]"));

    mockMvc.perform(post("/api/v1/templates/render")
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"templateName\":\"welcome\",\"parameters\":{}}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.message").value("Missing required variables: [name]"));
  }

  @Test
  void render_blankTemplateName_returns400() throws Exception {
    mockMvc.perform(post("/api/v1/templates/render")
            .with(jwt().authorities(() -> WRITE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"templateName\":\" \"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(renderTemplateUseCase);
  }

  @Test
  void render_withoutWriteScope_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/templates/render")
            .with(jwt().authorities(() -> READ))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"templateName\":\"welcome\"}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(renderTemplateUseCase);
  }

  @Test
  void render_noToken_returns401() throws Exception {
    mockMvc.perform(post("/api/v1/templates/render")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"templateName\":\"welcome\"}"))
        .andExpect(status().isUnauthorized());
  }
}
