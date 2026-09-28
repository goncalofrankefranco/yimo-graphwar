import test from 'node:test';
import assert from 'node:assert/strict';
import { runInNewContext } from 'node:vm';
import { ADMIN_PAGE } from '../src/pages.ts';

test('organizer panel explains proxy HTML failures instead of showing a JSON parse error', async () => {
  const nodes = new Map<string, any>();
  const node = (selector: string) => {
    if (!nodes.has(selector)) {
      nodes.set(selector, {
        value: selector === '#tournamentId' ? 'offline-test' : '',
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
  const fetch = async () => new Response('<html>Bad Gateway</html>', {
    status: 502,
    headers: { 'Content-Type': 'text/html' },
  });
  const scripts = [...ADMIN_PAGE.matchAll(/<script>([\s\S]*?)<\/script>/g)];
  const context: any = { document, sessionStorage, fetch };

  runInNewContext(scripts.at(-1)?.[1] ?? '', context);
  await context.refresh();

  assert.match(node('#status').textContent, /HTTP 502/);
  assert.match(node('#status').textContent, /temporarily unavailable/);
});

test('organizer panel gives a useful message when the service cannot be reached', async () => {
  const nodes = new Map<string, any>();
  const node = (selector: string) => {
    if (!nodes.has(selector)) nodes.set(selector, {
      value: selector === '#tournamentId' ? 'offline-test' : '', textContent: '', className: '',
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
  await context.refresh();

  assert.match(node('#status').textContent, /Could not reach the tournament service/);
});
