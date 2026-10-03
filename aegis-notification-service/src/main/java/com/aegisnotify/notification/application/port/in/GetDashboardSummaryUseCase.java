package com.aegisnotify.notification.application.port.in;

import com.aegisnotify.notification.application.dto.DashboardSummaryResponse;
import java.time.Duration;

public interface GetDashboardSummaryUseCase {

  DashboardSummaryResponse getSummary(Duration window);
}
