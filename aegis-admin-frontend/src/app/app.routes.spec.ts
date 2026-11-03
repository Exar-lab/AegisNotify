import { routes } from './app.routes';

describe('app routes', () => {
  const children = routes.find((route) => route.path === '')?.children ?? [];
  const index = (path: string) => children.findIndex((route) => route.path === path);

  it('declares the composer route before the notifications/:id detail route', () => {
    expect(index('notifications/new')).toBeGreaterThan(-1);
    expect(index('notifications/new')).toBeLessThan(index('notifications/:id'));
  });

  it('guards the composer route with a scope guard, and only that route', () => {
    expect(children[index('notifications/new')].canActivate).toHaveLength(1);
    expect(children[index('notifications/:id')].canActivate).toBeUndefined();
  });
});
