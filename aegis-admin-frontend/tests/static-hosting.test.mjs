import assert from 'node:assert/strict';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';
import { mkdtemp, readFile, rename, rmdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const baseUrl = process.env.AEGIS_STATIC_URL ?? 'http://localhost:4300';

test('direct Angular routes, including nested and unknown routes, serve the Angular document', async () => {
  for (const path of ['/dashboard', '/notifications/example', '/templates/example/edit', '/unknown/nested']) {
    const response = await fetch(new URL(path, baseUrl));
    assert.equal(response.status, 200, path);
    assert.match(await response.text(), /<app-root[\s>]/, path);
  }
});

test('silent SSO serves the callback directly, never the Angular application', async () => {
  const response = await fetch(new URL('/silent-check-sso.html', baseUrl), { redirect: 'manual' });
  assert.equal(response.status, 200, 'The SSO callback must not redirect to a clean URL');
  const html = await response.text();
  assert.doesNotMatch(html, /<app-root[\s>]/, 'An Angular document here leaves parent initialization waiting');
  const script = html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
  assert.ok(script, 'The callback must contain its postMessage script');
  const href = new URL('/silent-check-sso.html#code=test-code&state=test-state', baseUrl).href;
  const origin = new URL(baseUrl).origin;
  const messages = [];
  runInNewContext(script, {
    location: { href, origin },
    parent: { postMessage: (...args) => messages.push(args) },
  });
  assert.deepEqual(messages, [[href, origin]], 'The callback must return the auth response to its own origin');
});

test('runtime configuration remains a public JSON asset without cache', async () => {
  const response = await fetch(new URL('/config.json', baseUrl), { cache: 'no-store' });
  assert.equal(response.status, 200);
  assert.match(response.headers.get('cache-control') ?? '', /no-store/);
  const config = await response.json();
  assert.equal(typeof config, 'object');
  assert.notEqual(config, null);
  assert.equal(Array.isArray(config), false);
});

test('missing config.json returns a real 404 instead of the SPA document', async () => {
  const configPath = fileURLToPath(new URL('../dist/aegis-admin-frontend/browser/config.json', import.meta.url));
  const original = await readFile(configPath, 'utf8');
  const before = await fetch(new URL('/config.json', baseUrl), { cache: 'no-store' });
  assert.equal(before.status, 200);
  assert.equal(await before.text(), original, 'The server must serve this local build before its JSON is moved');
  const temporaryDirectory = await mkdtemp(join(tmpdir(), 'aegis-hosting-'));
  const backupPath = join(temporaryDirectory, 'config.json');
  let moved = false;
  try {
    await rename(configPath, backupPath);
    moved = true;
    const response = await fetch(new URL('/config.json', baseUrl), {
      cache: 'no-store', redirect: 'manual',
    });
    assert.equal(response.status, 404, 'A missing config.json must bypass the SPA rewrite');
    assert.doesNotMatch(await response.text(), /<app-root[\s>]/);
    const dashboard = await fetch(new URL('/dashboard', baseUrl));
    assert.equal(dashboard.status, 200, 'Angular routes must still work with no config.json');
  } finally {
    if (moved) await rename(backupPath, configPath);
    await rmdir(temporaryDirectory);
  }
  const restored = await fetch(new URL('/config.json', baseUrl), { cache: 'no-store' });
  assert.equal(restored.status, 200);
  assert.equal(await restored.text(), original);
  assert.match(restored.headers.get('cache-control') ?? '', /no-store/);
});
