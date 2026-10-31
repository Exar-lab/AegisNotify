export interface ManagedUser {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
  createdAt: string;
  roles: string[];
}

/** Mirrors aegis-user-service PagedResult (page is zero-based). */
export interface UserPage {
  content: ManagedUser[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
}

export interface UpdateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
}

export interface SetUserStatusRequest {
  enabled: boolean;
}

export interface ResetPasswordRequest {
  newPassword: string;
  temporary?: boolean;
}

export interface SetUserRolesRequest {
  roles: string[];
}
