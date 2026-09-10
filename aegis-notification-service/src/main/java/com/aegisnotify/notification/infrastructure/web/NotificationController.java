package com.aegisnotify.notification.infrastructure.web;

import com.aegisnotify.notification.application.dto.NotificationResponse;
import com.aegisnotify.notification.application.dto.NotificationStatusResponse;
import com.aegisnotify.notification.application.dto.NotificationSummary;
import com.aegisnotify.notification.application.port.in.CancelNotificationUseCase;
import com.aegisnotify.notification.application.port.in.CreateNotificationUseCase;
import com.aegisnotify.notification.application.port.in.GetNotificationStatusUseCase;
import com.aegisnotify.notification.application.port.in.ListNotificationsUseCase;
import com.aegisnotify.notification.application.port.in.RetryFailedNotificationUseCase;
import com.aegisnotify.notification.domain.enums.Channel;
import com.aegisnotify.notification.domain.enums.NotificationStatus;
import com.aegisnotify.notification.infrastructure.web.dto.CreateNotificationRequest;
import com.aegisnotify.notification.infrastructure.web.mapper.NotificationWebMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final CreateNotificationUseCase createNotificationUseCase;
  private final GetNotificationStatusUseCase getNotificationStatusUseCase;
  private final CancelNotificationUseCase cancelNotificationUseCase;
  private final RetryFailedNotificationUseCase retryFailedNotificationUseCase;
  private final ListNotificationsUseCase listNotificationsUseCase;
  private final NotificationWebMapper mapper;

  public NotificationController(
      CreateNotificationUseCase createNotificationUseCase,
      GetNotificationStatusUseCase getNotificationStatusUseCase,
      CancelNotificationUseCase cancelNotificationUseCase,
      RetryFailedNotificationUseCase retryFailedNotificationUseCase,
      ListNotificationsUseCase listNotificationsUseCase,
      NotificationWebMapper mapper) {
    this.createNotificationUseCase = createNotificationUseCase;
    this.getNotificationStatusUseCase = getNotificationStatusUseCase;
    this.cancelNotificationUseCase = cancelNotificationUseCase;
    this.retryFailedNotificationUseCase = retryFailedNotificationUseCase;
    this.listNotificationsUseCase = listNotificationsUseCase;
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

  @PatchMapping("/{id}/cancel")
  public ResponseEntity<NotificationResponse> cancel(
      @PathVariable UUID id) {
    var response = cancelNotificationUseCase.cancel(id);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/retry")
  public ResponseEntity<NotificationResponse> retry(
      @PathVariable UUID id) {
    var response = retryFailedNotificationUseCase.retry(id);
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<List<NotificationSummary>> list(
      @RequestParam(required = false) Channel channel,
      @RequestParam(required = false) NotificationStatus status) {
    var response = listNotificationsUseCase.list(channel, status);
    return ResponseEntity.ok(response);
  }
}
