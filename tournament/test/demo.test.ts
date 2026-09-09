import test from 'node:test';
import assert from 'node:assert/strict';
import { createDemoService, DEMO_TOURNAMENT_ID } from '../src/demo.ts';

test('creates a disposable bracket that is visible through the public view', () => {
  const { service, matchCodes } = createDemoService();
  const bracket = service.publicBracket(DEMO_TOURNAMENT_ID);
  assert.equal(bracket.name, 'YIMO Practice Cup');
  assert.equal(bracket.matches.length, 7);
  assert.equal(Object.keys(matchCodes).length, 4);
  assert.ok(bracket.matches.every((match) => !('matchCode' in match)));
  service.close();
});
