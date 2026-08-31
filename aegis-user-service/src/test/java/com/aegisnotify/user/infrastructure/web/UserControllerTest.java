package com.aegisnotify.user.infrastructure.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegisnotify.user.application.dto.NewUser;
import com.aegisnotify.user.application.dto.PagedResult;
import com.aegisnotify.user.application.dto.UserUpdate;
import com.aegisnotify.user.application.port.in.ManageUsersUseCase;
import com.aegisnotify.user.application.port.in.QueryUsersUseCase;
import com.aegisnotify.user.domain.exception.UserAlreadyExistsException;
import com.aegisnotify.user.domain.exception.UserNotFoundException;
import com.aegisnotify.user.domain.model.ManagedUser;
import com.aegisnotify.user.infrastructure.config.SecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies {@link UserController}'s read and write endpoints, including the
 * non-hierarchical scope gating requirement (D3): {@code user:admin} does
 * NOT implicitly grant {@code user:read}, and {@code user:read} does NOT
 * implicitly grant {@code user:admin} — each direction is tested
 * separately below.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

  private static final ManagedUser SAMPLE_USER = new ManagedUser(
      "u-1", "jdoe", "jdoe@example.com", "Jane", "Doe", true,
      Instant.parse("2026-01-01T00:00:00Z"));

  private static final ManagedUser DISABLED_USER = new ManagedUser(
      "u-1", "jdoe", "jdoe@example.com", "Jane", "Doe", false,
      Instant.parse("2026-01-01T00:00:00Z"));

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private QueryUsersUseCase queryUsersUseCase;

  @MockitoBean
  private ManageUsersUseCase manageUsersUseCase;

  @Test
  void listUsers_withUserReadScope_returns200() throws Exception {
    when(queryUsersUseCase.listUsers(0, 20))
        .thenReturn(new PagedResult<>(List.of(SAMPLE_USER), 0, 20, 1, 1));

    mockMvc.perform(get("/api/v1/users")
            .with(jwt().authorities(() -> "SCOPE_user:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].username").value("jdoe"))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void listUsers_withOnlyUserAdminScope_returns403() throws Exception {
    // D3: user:admin must NOT implicitly grant user:read on read endpoints.
    mockMvc.perform(get("/api/v1/users")
            .with(jwt().authorities(() -> "SCOPE_user:admin")))
        .andExpect(status().isForbidden());
  }

  @Test
  void listUsers_noToken_returns401NotForbidden() throws Exception {
    mockMvc.perform(get("/api/v1/users"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getUser_withUserReadScope_returns200() throws Exception {
    when(queryUsersUseCase.getUser("u-1")).thenReturn(SAMPLE_USER);

    mockMvc.perform(get("/api/v1/users/{id}", "u-1")
            .with(jwt().authorities(() -> "SCOPE_user:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("u-1"))
        .andExpect(jsonPath("$.username").value("jdoe"));
  }

  @Test
  void getUser_withOnlyUserAdminScope_returns403() throws Exception {
    // D3: user:admin must NOT implicitly grant user:read on read endpoints.
    mockMvc.perform(get("/api/v1/users/{id}", "u-1")
            .with(jwt().authorities(() -> "SCOPE_user:admin")))
        .andExpect(status().isForbidden());
  }

  @Test
  void getUser_notFound_returns404() throws Exception {
    when(queryUsersUseCase.getUser("missing"))
        .thenThrow(new UserNotFoundException("missing"));

    mockMvc.perform(get("/api/v1/users/{id}", "missing")
            .with(jwt().authorities(() -> "SCOPE_user:read")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void deleteUser_noRouteExists_returns405NotForbidden() throws Exception {
    // D4: confirms no accidental delete route exists. The JWT deliberately
    // carries user:read (the only scope this slice's path matcher checks)
    // so the 405 below is proven to come from "no handler for DELETE", not
    // from scope denial — a caller who is otherwise fully authorized on this
    // path still cannot delete, because the route was never mapped.
    mockMvc.perform(delete("/api/v1/users/{id}", "u-1")
            .with(jwt().authorities(() -> "SCOPE_user:read")))
        .andExpect(status().isMethodNotAllowed());
  }

  // --- Slice 5b: mutation endpoints, gated by user:admin (D3) ---

  @Test
  void createUser_withUserAdminScope_returns201() throws Exception {
    when(manageUsersUseCase.createUser(new NewUser("jdoe", "jdoe@example.com", "Jane", "Doe")))
        .thenReturn(SAMPLE_USER);

    mockMvc.perform(post("/api/v1/users")
            .with(jwt().authorities(() -> "SCOPE_user:admin"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"jdoe\",\"email\":\"jdoe@example.com\","
                + "\"firstName\":\"Jane\",\"lastName\":\"Doe\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("u-1"))
        .andExpect(jsonPath("$.username").value("jdoe"));
  }

  @Test
  void createUser_withOnlyUserReadScope_returns403() throws Exception {
    // D3: user:read must NOT implicitly grant user:admin on mutation
    // endpoints.
    mockMvc.perform(post("/api/v1/users")
            .with(jwt().authorities(() -> "SCOPE_user:read"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"jdoe\",\"email\":\"jdoe@example.com\","
                + "\"firstName\":\"Jane\",\"lastName\":\"Doe\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void createUser_withOnlyUserAdminScope_noUserReadNeeded_returns201() throws Exception {
    // Confirms non-hierarchical gating in the OTHER direction too: a token
    // holding ONLY user:admin (no user:read at all) still succeeds on a
    // mutation endpoint — user:admin does not need user:read alongside it.
    when(manageUsersUseCase.createUser(new NewUser("jdoe", "jdoe@example.com", "Jane", "Doe")))
        .thenReturn(SAMPLE_USER);

    mockMvc.perform(post("/api/v1/users")
            .with(jwt().authorities(() -> "SCOPE_user:admin"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"jdoe\",\"email\":\"jdoe@example.com\","
                + "\"firstName\":\"Jane\",\"lastName\":\"Doe\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void createUser_duplicateUsername_returns409() throws Exception {
    when(manageUsersUseCase.createUser(new NewUser("jdoe", "jdoe@example.com", "Jane", "Doe")))
        .thenThrow(new UserAlreadyExistsException("jdoe"));

    mockMvc.perform(post("/api/v1/users")
            .with(jwt().authorities(() -> "SCOPE_user:admin"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"jdoe\",\"email\":\"jdoe@example.com\","
                + "\"firstName\":\"Jane\",\"lastName\":\"Doe\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void updateUser_withUserAdminScope_returns200() throws Exception {
    when(manageUsersUseCase.updateUser("u-1",
        new UserUpdate("jdoe@example.com", "Jane", "Doe")))
        .thenReturn(SAMPLE_USER);

    mockMvc.perform(put("/api/v1/users/{id}", "u-1")
            .with(jwt().authorities(() -> "SCOPE_user:admin"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"jdoe@example.com\",\"firstName\":\"Jane\","
                + "\"lastName\":\"Doe\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("u-1"));
  }

  @Test
  void updateUser_withOnlyUserReadScope_returns403() throws Exception {
    mockMvc.perform(put("/api/v1/users/{id}", "u-1")
            .with(jwt().authorities(() -> "SCOPE_user:read"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"jdoe@example.com\",\"firstName\":\"Jane\","
                + "\"lastName\":\"Doe\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void setStatus_disableWithUserAdminScope_returns200() throws Exception {
    when(manageUsersUseCase.setUserEnabled("u-1", false)).thenReturn(DISABLED_USER);

    mockMvc.perform(patch("/api/v1/users/{id}/status", "u-1")
            .with(jwt().authorities(() -> "SCOPE_user:admin"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  void setStatus_disablingAlreadyDisabledUser_isIdempotentReturns200() throws Exception {
    when(manageUsersUseCase.setUserEnabled("u-1", false)).thenReturn(DISABLED_USER);

    mockMvc.perform(patch("/api/v1/users/{id}/status", "u-1")
            .with(jwt().authorities(() -> "SCOPE_user:admin"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}"))
        .andExpect(status().isOk());
    mockMvc.perform(patch("/api/v1/users/{id}/status", "u-1")
            .with(jwt().authorities(() -> "SCOPE_user:admin"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  void setStatus_withOnlyUserReadScope_returns403() throws Exception {
    mockMvc.perform(patch("/api/v1/users/{id}/status", "u-1")
            .with(jwt().authorities(() -> "SCOPE_user:read"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void resetPassword_withUserAdminScope_returns200() throws Exception {
    mockMvc.perform(put("/api/v1/users/{id}/password", "u-1")
            .with(jwt().authorities(() -> "SCOPE_user:admin"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newPassword\":\"NewPassw0rd!\",\"temporary\":true}"))
        .andExpect(status().isOk());
  }

  @Test
  void resetPassword_withOnlyUserReadScope_returns403() throws Exception {
    mockMvc.perform(put("/api/v1/users/{id}/password", "u-1")
            .with(jwt().authorities(() -> "SCOPE_user:read"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newPassword\":\"NewPassw0rd!\",\"temporary\":true}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void getUser_disabledUser_stillReturnedByGetWithUserReadScope() throws Exception {
    // D4: disable is not delete — a disabled user must remain fully
    // visible through the read path.
    when(queryUsersUseCase.getUser("u-1")).thenReturn(DISABLED_USER);

    mockMvc.perform(get("/api/v1/users/{id}", "u-1")
            .with(jwt().authorities(() -> "SCOPE_user:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("u-1"))
        .andExpect(jsonPath("$.enabled").value(false));
  }
}
