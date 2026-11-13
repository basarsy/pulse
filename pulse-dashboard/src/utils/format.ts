import type { CampaignStatus, CommandStatus, ConnectivityState, UpdateStatus } from '../api/types';

type BadgeColor = 'green' | 'red' | 'yellow' | 'blue' | 'gray';

export function timeAgo(iso: string | null | undefined): string {
  if (!iso) return 'never';
  const seconds = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return `${Math.max(seconds, 0)}s ago`;
  if (seconds < 3600) return `${Math.round(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.round(seconds / 3600)}h ago`;
  return new Date(iso).toLocaleDateString();
}

export function dateTime(iso: string | null | undefined): string {
  return iso ? new Date(iso).toLocaleString() : '—';
}

export function num(value: number | null | undefined, digits = 0, unit = ''): string {
  if (value === null || value === undefined) return '—';
  return `${value.toLocaleString(undefined, { maximumFractionDigits: digits, minimumFractionDigits: digits })}${unit}`;
}

export function label(value: string): string {
  return value.replace(/_/g, ' ');
}

export const connectivityColor: Record<ConnectivityState, BadgeColor> = {
  ONLINE: 'green', OFFLINE: 'red', SLEEPING: 'yellow',
};

export const commandColor: Record<CommandStatus, BadgeColor> = {
  PENDING: 'gray', SENT: 'blue', ACKNOWLEDGED: 'blue', COMPLETED: 'green', FAILED: 'red', TIMED_OUT: 'yellow',
};

export const campaignColor: Record<CampaignStatus, BadgeColor> = {
  DRAFT: 'gray', IN_PROGRESS: 'blue', PAUSED: 'yellow', COMPLETED: 'green', ABORTED: 'red',
};

export const updateColor: Record<UpdateStatus, BadgeColor> = {
  PENDING: 'gray', DOWNLOADING: 'blue', DOWNLOADED: 'blue', INSTALLING: 'blue',
  INSTALLED: 'green', FAILED: 'red', ROLLED_BACK: 'yellow',
};

/** Upserts {@code item} into a list by id, keeping the list order (new items first). */
export function upsertById<T extends { id: string }>(list: T[] | undefined, item: T): T[] {
  const items = list ?? [];
  return items.some(existing => existing.id === item.id)
    ? items.map(existing => (existing.id === item.id ? item : existing))
    : [item, ...items];
}
