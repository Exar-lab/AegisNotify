import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RUNTIME_CONFIG } from '../config/runtime-config';
import {
  CreateUserRequest,
  ManagedUser,
  ResetPasswordRequest,
  SetUserStatusRequest,
  UpdateUserRequest,
  UserPage,
} from '../models/user.models';

@Injectable({ providedIn: 'root' })
export class UsersApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(RUNTIME_CONFIG).apiBaseUrl.replace(/\/+$/, '');
  private readonly usersUrl = `${this.baseUrl}/api/v1/users`;

  list(page = 0, size = 20): Observable<UserPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<UserPage>(this.usersUrl, { params });
  }

  get(id: string): Observable<ManagedUser> {
    return this.http.get<ManagedUser>(`${this.usersUrl}/${encodeURIComponent(id)}`);
  }

  create(body: CreateUserRequest): Observable<ManagedUser> {
    return this.http.post<ManagedUser>(this.usersUrl, body);
  }

  update(id: string, body: UpdateUserRequest): Observable<ManagedUser> {
    return this.http.put<ManagedUser>(`${this.usersUrl}/${encodeURIComponent(id)}`, body);
  }

  setStatus(id: string, enabled: boolean): Observable<ManagedUser> {
    const body: SetUserStatusRequest = { enabled };
    return this.http.patch<ManagedUser>(`${this.usersUrl}/${encodeURIComponent(id)}/status`, body);
  }

  resetPassword(id: string, body: ResetPasswordRequest): Observable<void> {
    return this.http.put<void>(`${this.usersUrl}/${encodeURIComponent(id)}/password`, body);
  }
}
