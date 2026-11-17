import { describe, expect, it } from 'vitest';
import { mergeLoaded, newer, upsertById } from './versions';

const item = (id: string, version: number, label = `${id}@${version}`) => ({ id, version, label });

describe('newer', () => {
  it('keeps what is shown when the incoming state is older', () => {
    const shown = item('a', 3);
    expect(newer(shown, item('a', 2))).toBe(shown);
  });

  it('takes the incoming state when it is newer or the same version', () => {
    const later = item('a', 4);
    const same = item('a', 3, 'same commit');
    expect(newer(item('a', 3), later)).toBe(later);
    expect(newer(item('a', 3), same)).toBe(same);
  });

  it('takes the incoming state when nothing is shown or it is another item', () => {
    const incoming = item('b', 1);
    expect(newer(undefined, incoming)).toBe(incoming);
    expect(newer(item('a', 9), incoming)).toBe(incoming);
  });
});

describe('upsertById', () => {
  it('ignores an update older than the item shown', () => {
    const list = [item('a', 5), item('b', 1)];
    expect(upsertById(list, item('a', 4))).toEqual(list);
  });

  it('replaces the item in place with a newer or equal version', () => {
    expect(upsertById([item('a', 5), item('b', 1)], item('b', 2))).toEqual([item('a', 5), item('b', 2)]);
    expect(upsertById([item('a', 5)], item('a', 5, 'again'))).toEqual([item('a', 5, 'again')]);
  });

  it('puts a new item first', () => {
    expect(upsertById([item('a', 1)], item('b', 0))).toEqual([item('b', 0), item('a', 1)]);
    expect(upsertById(undefined, item('a', 0))).toEqual([item('a', 0)]);
  });
});

describe('mergeLoaded', () => {
  it('keeps a live update that is newer than the loaded snapshot', () => {
    const live = item('a', 7);
    expect(mergeLoaded([live], [item('a', 6), item('b', 1)])).toEqual([live, item('b', 1)]);
  });

  it('takes the loaded item when it is newer or the same version', () => {
    expect(mergeLoaded([item('a', 6)], [item('a', 7)])).toEqual([item('a', 7)]);
    expect(mergeLoaded([item('a', 6)], [item('a', 6, 'loaded')])).toEqual([item('a', 6, 'loaded')]);
  });

  it('shows exactly the loaded items, in their order', () => {
    expect(mergeLoaded([item('x', 9)], [item('b', 1), item('a', 1)])).toEqual([item('b', 1), item('a', 1)]);
    expect(mergeLoaded(undefined, [item('a', 1)])).toEqual([item('a', 1)]);
  });
});
