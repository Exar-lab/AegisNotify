package com.aegisnotify.notification.infrastructure.web;

import com.aegisnotify.notification.application.dto.TemplateResponse;
import com.aegisnotify.notification.application.port.in.CreateTemplateUseCase;
import com.aegisnotify.notification.application.port.in.GetTemplateUseCase;
import com.aegisnotify.notification.application.port.in.ListTemplatesUseCase;
import com.aegisnotify.notification.application.port.in.SetTemplateStatusUseCase;
import com.aegisnotify.notification.application.port.in.UpdateTemplateUseCase;
import com.aegisnotify.notification.infrastructure.web.dto.CreateTemplateRequest;
import com.aegisnotify.notification.infrastructure.web.dto.SetTemplateStatusRequest;
import com.aegisnotify.notification.infrastructure.web.dto.UpdateTemplateRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing notification templates.
 *
 * <p>Reads (GET) require {@code notification:read}; mutations
 * (POST/PUT/PATCH) require {@code notification:write}, enforced in
 * {@code SecurityConfig}. There is no delete: deactivating a template via
 * {@link #setStatus} is the lifecycle mutation, and it is idempotent.
 * {@code name} and {@code channel} are immutable after creation.</p>
 */
@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

  private final ListTemplatesUseCase listTemplatesUseCase;
  private final GetTemplateUseCase getTemplateUseCase;
  private final CreateTemplateUseCase createTemplateUseCase;
  private final UpdateTemplateUseCase updateTemplateUseCase;
  private final SetTemplateStatusUseCase setTemplateStatusUseCase;

  public TemplateController(
      ListTemplatesUseCase listTemplatesUseCase,
      GetTemplateUseCase getTemplateUseCase,
      CreateTemplateUseCase createTemplateUseCase,
      UpdateTemplateUseCase updateTemplateUseCase,
      SetTemplateStatusUseCase setTemplateStatusUseCase) {
    this.listTemplatesUseCase = listTemplatesUseCase;
    this.getTemplateUseCase = getTemplateUseCase;
    this.createTemplateUseCase = createTemplateUseCase;
    this.updateTemplateUseCase = updateTemplateUseCase;
    this.setTemplateStatusUseCase = setTemplateStatusUseCase;
  }

  /**
   * Lists all templates (active and inactive), ordered by name. Not
   * paginated: the expected template count is small.
   *
   * @return all templates
   */
  @GetMapping
  public ResponseEntity<List<TemplateResponse>> list() {
    return ResponseEntity.ok(listTemplatesUseCase.list());
  }

  /**
   * Returns a single template.
   *
   * @param id the template id
   * @return the template, or 404 if unknown
   */
  @GetMapping("/{id}")
  public ResponseEntity<TemplateResponse> get(@PathVariable UUID id) {
    return ResponseEntity.ok(getTemplateUseCase.get(id));
  }

  /**
   * Creates a new, active template.
   *
   * @param request the template fields
   * @return the created template, 201; 409 if the name is taken
   */
  @PostMapping
  public ResponseEntity<TemplateResponse> create(
      @Valid @RequestBody CreateTemplateRequest request) {
    TemplateResponse created = createTemplateUseCase.create(request.toCommand());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /**
   * Replaces the editable content ({@code subject}, {@code body},
   * {@code variables}) of a template.
   *
   * @param id the template id
   * @param request the new content
   * @return the updated template, 200; 404 if unknown
   */
  @PutMapping("/{id}")
  public ResponseEntity<TemplateResponse> update(@PathVariable UUID id,
      @Valid @RequestBody UpdateTemplateRequest request) {
    return ResponseEntity.ok(updateTemplateUseCase.update(id, request.toCommand()));
  }

  /**
   * Activates or deactivates a template. Idempotent.
   *
   * @param id the template id
   * @param request the desired active state
   * @return the template, 200; 404 if unknown
   */
  @PatchMapping("/{id}/status")
  public ResponseEntity<TemplateResponse> setStatus(@PathVariable UUID id,
      @Valid @RequestBody SetTemplateStatusRequest request) {
    return ResponseEntity.ok(setTemplateStatusUseCase.setStatus(id, request.active()));
  }
}
