package com.aegisnotify.notification.application.port.in;

public interface FlushAggregationWindowsUseCase {

  /**
   * Claims every buffered notification whose window has expired (or whose
   * claim lease has lapsed) and resolves each claimed group. Slice 1 has no
   * summarizer wired in yet, so every group resolves via individual delivery
   * — the same shape as the pre-aggregation outbox path, one outbox event per
   * notification.
   *
   * @return the number of buffered notifications resolved in this poll
   */
  int flushExpiredWindows();
}
