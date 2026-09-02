package com.aegisnotify.user.application.service;

import com.aegisnotify.user.application.dto.PagedResult;
import com.aegisnotify.user.application.port.in.QueryUsersUseCase;
import com.aegisnotify.user.application.port.out.UserDirectoryPort;
import com.aegisnotify.user.domain.model.ManagedUser;
import org.springframework.stereotype.Service;

/**
 * Sole {@link QueryUsersUseCase} implementation, delegating to {@link
 * UserDirectoryPort} without any additional orchestration in this slice.
 */
@Service
public class UserQueryService implements QueryUsersUseCase {

  private final UserDirectoryPort userDirectoryPort;

  public UserQueryService(UserDirectoryPort userDirectoryPort) {
    this.userDirectoryPort = userDirectoryPort;
  }

  @Override
  public PagedResult<ManagedUser> listUsers(int page, int size) {
    return userDirectoryPort.findAll(page, size);
  }

  @Override
  public ManagedUser getUser(String id) {
    return userDirectoryPort.findById(id);
  }
}
