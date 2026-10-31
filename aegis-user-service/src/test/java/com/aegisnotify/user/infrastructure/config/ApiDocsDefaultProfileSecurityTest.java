package com.aegisnotify.user.infrastructure.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisnotify.docs.ApiDocsAutoConfiguration;
import com.aegisnotify.user.application.port.in.ManageUsersUseCase;
import com.aegisnotify.user.application.port.in.QueryUsersUseCase;
import com.aegisnotify.user.infrastructure.web.UserController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Without the {@code local} profile the docs paths stay behind the default
 * {@code denyAll()} (401 anonymous, 403 authenticated): the profile-gated
 * {@link ApiDocsAutoConfiguration} must not contribute its permitAll chain.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, ApiDocsAutoConfiguration.class})
class ApiDocsDefaultProfileSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private QueryUsersUseCase queryUsersUseCase;

  @MockitoBean
  private ManageUsersUseCase manageUsersUseCase;

  @ParameterizedTest
  @ValueSource(strings = {"/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui.html"})
  void docsPaths_withoutJwt_return401(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui.html"})
  void docsPaths_withAuthenticatedJwt_return403(String path) throws Exception {
    mockMvc.perform(get(path).with(jwt())).andExpect(status().isForbidden());
  }
}
