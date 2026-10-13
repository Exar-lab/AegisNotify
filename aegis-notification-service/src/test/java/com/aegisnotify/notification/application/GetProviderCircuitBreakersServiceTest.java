package com.aegisnotify.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aegisnotify.notification.application.dto.ProviderCircuitBreakerStatus;
import com.aegisnotify.notification.application.port.out.CircuitBreakerInspectionPort;
import com.aegisnotify.notification.application.service.GetProviderCircuitBreakersService;
import com.aegisnotify.notification.domain.enums.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetProviderCircuitBreakersServiceTest {

  @Mock
  private CircuitBreakerInspectionPort circuitBreakerInspectionPort;

  @InjectMocks
  private GetProviderCircuitBreakersService service;

  @Test
  void getCircuitBreakers_delegatesToInspectionPort() {
    List<ProviderCircuitBreakerStatus> expected = List.of(
        new ProviderCircuitBreakerStatus(
            "email-provider", "SendGrid", Channel.EMAIL, "PRIMARY",
            "SendGrid Secondary", "CLOSED"
        )
    );
    when(circuitBreakerInspectionPort.getProviderCircuitBreakerStatuses())
        .thenReturn(expected);

    List<ProviderCircuitBreakerStatus> actual = service.getCircuitBreakers();

    verify(circuitBreakerInspectionPort).getProviderCircuitBreakerStatuses();
    assertEquals(expected, actual);
  }
}

