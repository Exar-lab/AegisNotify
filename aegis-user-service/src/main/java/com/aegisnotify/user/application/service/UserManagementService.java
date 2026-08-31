package com.aegisnotify.user.application.service;

import com.aegisnotify.user.application.dto.NewUser;
import com.aegisnotify.user.application.dto.UserUpdate;
import com.aegisnotify.user.application.port.in.ManageUsersUseCase;
import com.aegisnotify.user.application.port.out.UserDirectoryPort;
import com.aegisnotify.user.domain.model.ManagedUser;
import org.springframework.stereotype.Service;

/**
 * Sole {@link ManageUsersUseCase} implementation, delegating to {@link
 * UserDirectoryPort} without any additional orchestration in this slice.
 */
@Service
public class UserManagementService implements ManageUsersUseCase {

  private final UserDirectoryPort userDirectoryPort;

  public UserManagementService(UserDirectoryPort userDirectoryPort) {
    this.userDirectoryPort = userDirectoryPort;
  }

  @Override
  public ManagedUser createUser(NewUser newUser) {
    return userDirectoryPort.create(newUser);
  }

  @Override
  public ManagedUser updateUser(String id, UserUpdate update) {
    return userDirectoryPort.update(id, update);
  }

  @Override
  public ManagedUser setUserEnabled(String id, boolean enabled) {
    return userDirectoryPort.setEnabled(id, enabled);
  }

  @Override
  public void resetPassword(String id, String newPassword, boolean temporary) {
    userDirectoryPort.resetPassword(id, newPassword, temporary);
  }
}
