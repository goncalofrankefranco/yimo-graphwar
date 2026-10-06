import test from 'node:test';
import assert from 'node:assert/strict';
import { runInNewContext } from 'node:vm';
import { ADMIN_PAGE } from '../src/pages.ts';

test('clears a rejected saved organizer password and explains how to recover', async () => {
  const nodes = new Map<string, any>();
  const node = (selector: string) => {
    if (!nodes.has(selector)) nodes.set(selector, {
      value: '', textContent: '', className: '',
      appendChild() {}, replaceChildren() {}, addEventListener() {}, focus() { this.focused = true; },
    });
    return nodes.get(selector);
  };
  const removed: string[] = [];
  const context: any = {
    document: { querySelector: node, querySelectorAll: () => [] },
    sessionStorage: {
      getItem: () => 'stale-organizer-password', setItem() {},
      removeItem: (key: string) => removed.push(key),
    },
    fetch: async (path: string) => path === '/api/v1/tournaments/active'
      ? new Response('null', { status: 200, headers: { 'Content-Type': 'application/json' } })
      : new Response(JSON.stringify({ error: 'UNAUTHORIZED' }), {
        status: 401, headers: { 'Content-Type': 'application/json' },
      }),
  };
  const scripts = [...ADMIN_PAGE.matchAll(/<script>([\s\S]*?)<\/script>/g)];
  runInNewContext(scripts.at(-1)?.[1] ?? '', context);
  await new Promise((resolve) => setTimeout(resolve, 10));

  assert.deepEqual(removed, ['yimoAdminToken']);
  assert.equal(node('#token').value, '');
  assert.equal(node('#token').focused, true);
  assert.match(node('#status').textContent, /Organizer password was rejected or expired/);
});

test('organizer panel explains proxy HTML failures instead of showing a JSON parse error', async () => {
  const nodes = new Map<string, any>();
  const node = (selector: string) => {
    if (!nodes.has(selector)) {
      nodes.set(selector, {
        value: '',
        textContent: '',
        className: '',
        appendChild() {},
        replaceChildren() {},
        addEventListener() {},
      });
    }
    return nodes.get(selector);
  };
  const document = { querySelector: node, querySelectorAll: () => [] };
  const sessionStorage = { getItem: () => 'admin-test-token', setItem() {} };
  const fetch = async (path: string) => path === '/api/v1/tournaments/active'
    ? new Response('null', { status: 200, headers: { 'Content-Type': 'application/json' } })
    : new Response('<html>Bad Gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html' } });
  const scripts = [...ADMIN_PAGE.matchAll(/<script>([\s\S]*?)<\/script>/g)];
  const context: any = { document, sessionStorage, fetch };

  runInNewContext(scripts.at(-1)?.[1] ?? '', context);
  await new Promise((resolve) => setTimeout(resolve, 10));

  assert.match(node('#status').textContent, /HTTP 502/);
  assert.match(node('#status').textContent, /temporarily unavailable/);
});

test('organizer panel gives a useful message when the service cannot be reached', async () => {
  const nodes = new Map<string, any>();
  const node = (selector: string) => {
    if (!nodes.has(selector)) nodes.set(selector, {
      value: '', textContent: '', className: '',
      appendChild() {}, replaceChildren() {}, addEventListener() {},
    });
    return nodes.get(selector);
  };
  const context: any = {
    document: { querySelector: node, querySelectorAll: () => [] },
    sessionStorage: { getItem: () => 'admin-test-token', setItem() {} },
    fetch: async () => { throw new Error('ECONNREFUSED'); },
  };
  const scripts = [...ADMIN_PAGE.matchAll(/<script>([\s\S]*?)<\/script>/g)];
  runInNewContext(scripts.at(-1)?.[1] ?? '', context);
  await new Promise((resolve) => setTimeout(resolve, 10));

  assert.match(node('#status').textContent, /Could not reach the tournament service/);
});
