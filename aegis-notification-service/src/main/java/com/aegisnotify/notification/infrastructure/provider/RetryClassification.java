package com.aegisnotify.notification.infrastructure.provider;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientRequestException;

/**
 * Decides whether a provider-call failure is transient (worth retrying on the
 * same provider) or permanent (fail immediately, let failover decide).
 * Package-private: HTTP status codes are an infrastructure concept and never
 * cross into the application layer — only the resulting boolean does.
 */
final class RetryClassification {

  private static final int TOO_MANY_REQUESTS = 429;
  private static final int MAX_CAUSE_DEPTH = 8;

  private RetryClassification() {
  }

  /** 429 and every 5xx are transient; every other status is a permanent rejection. */
  static boolean isRetryable(HttpStatusCode status) {
    return status.value() == TOO_MANY_REQUESTS || status.is5xxServerError();
  }

  /**
   * Transport-level allow-list for non-HTTP failures. Reactor's {@code block()}
   * wraps checked causes, so the cause chain is walked (bounded, to survive a
   * self-referential cause).
   */
  static boolean isRetryable(Throwable failure) {
    Throwable current = failure;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (current instanceof TimeoutException
          || current instanceof IOException
          || current instanceof WebClientRequestException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
