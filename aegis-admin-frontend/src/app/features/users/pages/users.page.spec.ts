import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ManagedUser, UserPage } from '../../../core/models/user.models';
import { UsersApiService } from '../../../core/services/users-api.service';
import { UsersPage } from './users.page';

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

const mockUserPage: UserPage = {
  content: [mockUser],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

class MockUsersApiService {
  list = vi.fn().mockReturnValue(of(mockUserPage));
  get = vi.fn().mockReturnValue(of(mockUser));
  create = vi.fn().mockReturnValue(of(mockUser));
  update = vi.fn().mockReturnValue(of(mockUser));
  setStatus = vi.fn().mockReturnValue(of({ ...mockUser, enabled: false }));
  resetPassword = vi.fn().mockReturnValue(of(undefined));
  getRoles = vi.fn().mockReturnValue(of(['admin', 'operator', 'viewer']));
  setRoles = vi.fn().mockReturnValue(of({ ...mockUser, roles: ['admin', 'operator'] }));
}

describe('UsersPage', () => {
  let mockApi: MockUsersApiService;

  beforeEach(async () => {
    mockApi = new MockUsersApiService();
    await TestBed.configureTestingModule({
      imports: [UsersPage],
      providers: [
        provideRouter([]),
        { provide: UsersApiService, useValue: mockApi },
      ],
    }).compileComponents();
  });

  it('should render users list from api.list', () => {
    const fixture = TestBed.createComponent(UsersPage);
    fixture.detectChanges();

    expect(mockApi.list).toHaveBeenCalledWith(0, 20, '');
    const element = fixture.nativeElement as HTMLElement;
    const rows = element.querySelectorAll('tbody tr');
    expect(rows.length).toBe(1);
    expect(element.textContent).toContain('jdoe');
    expect(element.textContent).toContain('jdoe@example.com');
    expect(element.textContent).toContain('Jane Doe');
    expect(element.textContent).toContain('admin');
    expect(element.textContent).toContain('Enabled');
  });

  it('should trigger search and reload page 0 when search changes', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(UsersPage);
    fixture.detectChanges();

    fixture.componentInstance.searchControl.setValue('jane');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    expect(mockApi.list).toHaveBeenCalledWith(0, 20, 'jane');
    expect(fixture.componentInstance.page()).toBe(0);
    expect(fixture.componentInstance.search()).toBe('jane');
    vi.useRealTimers();
  });

  it('should open create modal, validate fields and call api.create', () => {
    const fixture = TestBed.createComponent(UsersPage);
    fixture.detectChanges();

    fixture.componentInstance.openCreate();
    fixture.detectChanges();
    expect(fixture.componentInstance.isCreateOpen()).toBe(true);

    // Invalid submission
    fixture.componentInstance.submitCreate();
    expect(fixture.componentInstance.createForm.invalid).toBe(true);
    expect(mockApi.create).not.toHaveBeenCalled();

    // Fill valid data
    fixture.componentInstance.createForm.setValue({
      username: 'newuser',
      email: 'newuser@example.com',
      firstName: 'New',
      lastName: 'User',
    });
    fixture.componentInstance.submitCreate();
    expect(mockApi.create).toHaveBeenCalledWith({
      username: 'newuser',
      email: 'newuser@example.com',
      firstName: 'New',
      lastName: 'User',
    });
    expect(fixture.componentInstance.isCreateOpen()).toBe(false);
  });

  it('should open edit modal with prefilled data and call api.update', () => {
    const fixture = TestBed.createComponent(UsersPage);
    fixture.detectChanges();

    fixture.componentInstance.openEdit(mockUser);
    fixture.detectChanges();
    expect(fixture.componentInstance.isEditOpen()).toBe(true);
    expect(fixture.componentInstance.editForm.value).toEqual({
      email: 'jdoe@example.com',
      firstName: 'Jane',
      lastName: 'Doe',
    });

    fixture.componentInstance.editForm.patchValue({ firstName: 'Janet' });
    fixture.componentInstance.submitEdit();

    expect(mockApi.update).toHaveBeenCalledWith('u-1', {
      email: 'jdoe@example.com',
      firstName: 'Janet',
      lastName: 'Doe',
    });
    expect(fixture.componentInstance.isEditOpen()).toBe(false);
  });

  it('should load available roles and save selected roles via api.setRoles', () => {
    const fixture = TestBed.createComponent(UsersPage);
    fixture.detectChanges();

    fixture.componentInstance.openRoles(mockUser);
    fixture.detectChanges();
    expect(fixture.componentInstance.isRolesOpen()).toBe(true);
    expect(fixture.componentInstance.selectedRoles()).toEqual(['admin']);

    fixture.componentInstance.toggleRole('operator');
    expect(fixture.componentInstance.isRoleSelected('operator')).toBe(true);

    fixture.componentInstance.submitRoles();
    expect(mockApi.setRoles).toHaveBeenCalledWith('u-1', ['admin', 'operator']);
    expect(fixture.componentInstance.isRolesOpen()).toBe(false);
  });

  it('should open password modal and call api.resetPassword', () => {
    const fixture = TestBed.createComponent(UsersPage);
    fixture.detectChanges();

    fixture.componentInstance.openPassword(mockUser);
    fixture.detectChanges();
    expect(fixture.componentInstance.isPasswordOpen()).toBe(true);

    // Invalid short password
    fixture.componentInstance.passwordForm.setValue({
      newPassword: 'short',
      temporary: true,
    });
    fixture.componentInstance.submitPassword();
    expect(mockApi.resetPassword).not.toHaveBeenCalled();

    // Valid password
    fixture.componentInstance.passwordForm.setValue({
      newPassword: 'validPassword123!',
      temporary: false,
    });
    fixture.componentInstance.submitPassword();
    expect(mockApi.resetPassword).toHaveBeenCalledWith('u-1', {
      newPassword: 'validPassword123!',
      temporary: false,
    });
    expect(fixture.componentInstance.isPasswordOpen()).toBe(false);
  });

  it('should toggle user status between enabled and disabled via api.setStatus', () => {
    const fixture = TestBed.createComponent(UsersPage);
    fixture.detectChanges();

    fixture.componentInstance.toggleStatus(mockUser);
    expect(mockApi.setStatus).toHaveBeenCalledWith('u-1', false);
  });

  it('should verify no delete action exists', () => {
    const fixture = TestBed.createComponent(UsersPage);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const text = element.textContent?.toLowerCase() ?? '';
    expect(text).not.toContain('delete');

    const buttons = Array.from(element.querySelectorAll('button')).map(
      (b) => b.textContent?.trim().toLowerCase() ?? '',
    );
    expect(buttons.some((b) => b.includes('delete') || b.includes('remove'))).toBe(false);

    // Verify component has no delete method
    const comp = fixture.componentInstance as unknown as Record<string, unknown>;
    expect(comp['deleteUser']).toBeUndefined();
    expect(comp['removeUser']).toBeUndefined();
    expect(comp['delete']).toBeUndefined();
  });
});
