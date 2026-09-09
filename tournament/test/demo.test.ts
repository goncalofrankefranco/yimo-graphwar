import test from 'node:test';
import assert from 'node:assert/strict';
import { createDemoService, DEMO_TOURNAMENT_ID, runDemoToStart } from '../src/demo.ts';

test('creates a disposable scheduled bracket that is visible through the public view', () => {
  const demo = createDemoService(100);
  assert.equal(demo.service.publicBracket(DEMO_TOURNAMENT_ID).status, 'DRAFT');
  assert.equal(demo.participantCodes.length, 4);
  runDemoToStart(demo);
  const bracket = demo.service.publicBracket(DEMO_TOURNAMENT_ID);
  assert.equal(bracket.name, 'YIMO Practice Cup');
  assert.equal(bracket.status, 'RUNNING');
  assert.equal(bracket.matches.length, 3);
  assert.ok(bracket.matches.every((match) => !('matchCode' in match)));
  demo.service.close();
});
