import { describe, expect, it } from 'vitest';
import { randomUUID } from './uuid';

const V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('randomUUID', () => {
  it('uses crypto.randomUUID where the browser has it', () => {
    expect(randomUUID({
      getRandomValues: crypto.getRandomValues.bind(crypto),
      randomUUID: () => '00000000-0000-4000-8000-000000000000',
    })).toBe('00000000-0000-4000-8000-000000000000');
  });

  // Plain-HTTP hosts (not secure contexts) have no crypto.randomUUID.
  it('builds a version 4 UUID from getRandomValues without it', () => {
    const insecure = { getRandomValues: crypto.getRandomValues.bind(crypto) };
    const ids = new Set(Array.from({ length: 100 }, () => randomUUID(insecure)));
    ids.forEach(id => expect(id).toMatch(V4));
    expect(ids.size).toBe(100);
  });

  it('sets the version and variant bits whatever the random bytes are', () => {
    const allOnes = { getRandomValues: ((array: Uint8Array) => array.fill(0xff)) as Crypto['getRandomValues'] };
    expect(randomUUID(allOnes)).toBe('ffffffff-ffff-4fff-bfff-ffffffffffff');
  });
});
