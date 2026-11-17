// Live updates and REST loads can arrive in either order, so every command, vehicle update and
// rollout carries `version`: the version of its row when that state was committed, higher for
// every later state of the same item. These helpers never let an older state replace a newer one.
// An equal version is the same committed state (an action's REST response and its own live
// update), so the incoming copy is taken.

export interface Versioned {
  id: string;
  version: number;
}

/** The state to show for an item: {@code incoming}, unless the shown one is the same item and newer. */
export function newer<T extends Versioned>(shown: T | undefined, incoming: T): T {
  return shown && shown.id === incoming.id && shown.version > incoming.version ? shown : incoming;
}

/** Applies one update to a list by id, keeping its order; a new item goes first. */
export function upsertById<T extends Versioned>(list: T[] | undefined, item: T): T[] {
  const items = list ?? [];
  return items.some(existing => existing.id === item.id)
    ? items.map(existing => (existing.id === item.id ? newer(existing, item) : existing))
    : [item, ...items];
}

/**
 * Applies a freshly loaded list: it decides which items are shown and their order, and for an item
 * also shown already, the newer of the two states wins (a live update can beat a slower load).
 */
export function mergeLoaded<T extends Versioned>(shown: T[] | undefined, loaded: T[]): T[] {
  const byId = new Map((shown ?? []).map(item => [item.id, item]));
  return loaded.map(item => newer(byId.get(item.id), item));
}
