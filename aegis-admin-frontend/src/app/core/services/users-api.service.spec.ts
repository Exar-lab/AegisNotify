import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { ManagedUser, UserPage } from '../models/user.models';
import { UsersApiService } from './users-api.service';

const mockUser: ManagedUser = {
  id: 'u-1',
  username: 'jdoe',
  email: 'jdoe@example.com',
  firstName: 'Jane',
  lastName: 'Doe',
  enabled: true,
  createdAt: '2026-01-01T00:00:00Z',
  roles: ['admin'],
};

const mockPage: UserPage = {
  content: [mockUser],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe('UsersApiService', () => {
  let service: UsersApiService;
  let http: HttpTestingController;
  const baseUrl = environment.apiBaseUrl.replace(/\/+$/, '');
  const url = `${baseUrl}/api/v1/users`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UsersApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists users without search parameter', () => {
    service.list(0, 20).subscribe((res) => expect(res).toEqual(mockPage));
    const req = http.expectOne(`${url}?page=0&size=20`);
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('lists users with search parameter', () => {
    service.list(1, 10, 'jane').subscribe((res) => expect(res).toEqual(mockPage));
    const req = http.expectOne(`${url}?page=1&size=10&search=jane`);
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('gets a user by id', () => {
    service.get('u-1').subscribe((res) => expect(res).toEqual(mockUser));
    const req = http.expectOne(`${url}/u-1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockUser);
  });

  it('creates a new user', () => {
    const payload = {
      username: 'jdoe',
      email: 'jdoe@example.com',
      firstName: 'Jane',
      lastName: 'Doe',
    };
    service.create(payload).subscribe((res) => expect(res).toEqual(mockUser));
    const req = http.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(mockUser);
  });

  it('updates an existing user', () => {
    const payload = { email: 'updated@example.com', firstName: 'Janet', lastName: 'Doe' };
    service.update('u-1', payload).subscribe((res) => expect(res).toEqual(mockUser));
    const req = http.expectOne(`${url}/u-1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush(mockUser);
  });

  it('updates user enabled status', () => {
    service.setStatus('u-1', false).subscribe((res) => expect(res).toEqual(mockUser));
    const req = http.expectOne(`${url}/u-1/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ enabled: false });
    req.flush(mockUser);
  });

  it('resets user password', () => {
    service.resetPassword('u-1', { newPassword: 'secretPassword1', temporary: true }).subscribe();
    const req = http.expectOne(`${url}/u-1/password`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ newPassword: 'secretPassword1', temporary: true });
    req.flush(null);
  });

  it('retrieves available realm roles', () => {
    const roles = ['admin', 'operator', 'viewer'];
    service.getRoles().subscribe((res) => expect(res).toEqual(roles));
    const req = http.expectOne(`${url}/roles`);
    expect(req.request.method).toBe('GET');
    req.flush(roles);
  });

  it('sets user roles', () => {
    const roles = ['admin', 'operator'];
    service.setRoles('u-1', roles).subscribe((res) => expect(res).toEqual(mockUser));
    const req = http.expectOne(`${url}/u-1/roles`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ roles });
    req.flush(mockUser);
  });
});
