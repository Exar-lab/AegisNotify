import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { scopeGuard } from './scope.guard';

describe('scopeGuard', () => {
  function run(granted: boolean): boolean | UrlTree {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { hasScope: (scope: string) => granted && scope === 'notification:write' } },
      ],
    });
    return TestBed.runInInjectionContext(() => scopeGuard('notification:write')({} as never, {} as never)) as
      | boolean
      | UrlTree;
  }

  it('allows navigation when the user has the scope', () => {
    expect(run(true)).toBe(true);
  });

  it('redirects to the dashboard when the user lacks the scope', () => {
    const result = run(false);
    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/dashboard');
  });
});
