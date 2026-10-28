package com.aegisnotify.audit.infrastructure.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisnotify.audit.application.port.in.GetAuditTrailUseCase;
import com.aegisnotify.audit.application.port.in.SearchAuditEventsUseCase;
import com.aegisnotify.audit.infrastructure.web.AuditQueryController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Without the {@code local} profile the docs paths stay protected: the
 * profile-gated {@link ApiDocsConfig} must not contribute its permitAll chain.
 */
@WebMvcTest(AuditQueryController.class)
@Import({SecurityConfig.class, ApiDocsConfig.class})
class ApiDocsDefaultProfileSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private GetAuditTrailUseCase getAuditTrailUseCase;

  @MockitoBean
  private SearchAuditEventsUseCase searchAuditEventsUseCase;

  @ParameterizedTest
  @ValueSource(strings = {"/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui.html"})
  void docsPaths_withoutJwt_return401(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
  }
}
