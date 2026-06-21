package com.aegisnotify.notification.infrastructure.web;

import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.dto.NotificationStatusResponse;
import com.aegisnotify.notification.application.port.in.CreateNotificationUseCase;
import com.aegisnotify.notification.application.port.in.GetNotificationStatusUseCase;
import com.aegisnotify.notification.infrastructure.web.dto.CreateNotificationRequest;
import com.aegisnotify.notification.infrastructure.web.mapper.NotificationWebMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final CreateNotificationUseCase createNotificationUseCase;
  private final GetNotificationStatusUseCase getNotificationStatusUseCase;
  private final NotificationWebMapper mapper;

  public NotificationController(
      CreateNotificationUseCase createNotificationUseCase,
      GetNotificationStatusUseCase getNotificationStatusUseCase,
      NotificationWebMapper mapper) {
    this.createNotificationUseCase = createNotificationUseCase;
    this.getNotificationStatusUseCase = getNotificationStatusUseCase;
    this.mapper = mapper;
  }

  @PostMapping
  public ResponseEntity<NotificationResponse> create(
      @Valid @RequestBody CreateNotificationRequest request) {
    var command = mapper.toCommand(request);
    var response = createNotificationUseCase.create(command);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}/status")
        .buildAndExpand(response.id())
        .toUri();
    return ResponseEntity.accepted()
        .location(location)
        .body(response);
  }

  @GetMapping("/{id}/status")
  public ResponseEntity<NotificationStatusResponse> getStatus(
      @PathVariable UUID id) {
    var response = getNotificationStatusUseCase.getStatus(id);
    return ResponseEntity.ok(response);
  }
}
