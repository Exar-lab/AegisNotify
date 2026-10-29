package com.aegisnotify.user.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisnotify.docs.ApiDocsAutoConfiguration;
import com.aegisnotify.user.application.port.in.ManageUsersUseCase;
import com.aegisnotify.user.application.port.in.QueryUsersUseCase;
import com.aegisnotify.user.infrastructure.web.UserController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With the {@code local} profile the API-docs paths are public; everything
 * else keeps its existing protection. The {@code @WebMvcTest} slice does not
 * load springdoc, so "reachable" means security let the request through
 * (anything but 401/403, e.g. a dispatcher 404).
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, ApiDocsAutoConfiguration.class})
@ActiveProfiles("local")
class ApiDocsLocalProfileSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private QueryUsersUseCase queryUsersUseCase;

  @MockitoBean
  private ManageUsersUseCase manageUsersUseCase;

  @ParameterizedTest
  @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs/swagger-config",
      "/swagger-ui/index.html", "/swagger-ui.html"})
  void docsPaths_withoutJwt_areNotRejectedBySecurity(String path) throws Exception {
    int httpStatus = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();

    assertThat(httpStatus).isNotIn(401, 403);
  }

  @Test
  void businessEndpoint_withoutJwt_stillReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void docsPath_postWithoutCsrfToken_isRejected() throws Exception {
    mockMvc.perform(post("/v3/api-docs")).andExpect(status().isForbidden());
  }
}
