import type { Campaign, CampaignStatus, CommandStatus, ConnectivityState, UpdateStatus } from '../api/types';

import type { Tone } from './tone';
import type { Notification } from '../context/notifications';

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

/** An enum value as sentence-case text: 'IN_PROGRESS' → 'In progress'. */
export function label(value: string): string {
  const words = value.replace(/_/g, ' ').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export const connectivityTone: Record<ConnectivityState, Tone> = {
  ONLINE: 'ok', OFFLINE: 'bad', SLEEPING: 'warn',
};

export const commandTone: Record<CommandStatus, Tone> = {
  PENDING: 'neutral', SENT: 'info', ACKNOWLEDGED: 'info', COMPLETED: 'ok', FAILED: 'bad', TIMED_OUT: 'warn', CANCELLED: 'neutral',
};

export const campaignTone: Record<CampaignStatus, Tone> = {
  DRAFT: 'neutral', IN_PROGRESS: 'info', PAUSED: 'warn', COMPLETED: 'ok', ABORTED: 'bad',
};

export const updateTone: Record<UpdateStatus, Tone> = {
  PENDING: 'neutral', DOWNLOADING: 'info', DOWNLOADED: 'info', INSTALLING: 'info',
  INSTALLED: 'ok', FAILED: 'bad', ROLLED_BACK: 'warn',
};

/** Share of a campaign's targeted vehicles that have finished (installed or failed), as a whole percent. */
export function campaignProgress(c: Campaign): number {
  if (!c.totalVehiclesTargeted) return c.status === 'COMPLETED' ? 100 : 0;
  return Math.round(((c.vehiclesInstalled + c.vehiclesFailed) / c.totalVehiclesTargeted) * 100);
}

export const notificationTone: Record<Notification['type'], Tone> = {
  success: 'ok', error: 'bad', warning: 'warn', info: 'info',
};

/** A clock time for logs, e.g. 14:05:09. */
export function clock(date: Date): string {
  return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
}

/**
 * The two segments of a campaign's progress bar, in percent: installed, then failed. A completed
 * campaign that targeted nobody (every vehicle was already up to date) shows as fully installed.
 */
export function campaignBar(c: Campaign): { installed: number; failed: number } {
  if (!c.totalVehiclesTargeted) return { installed: campaignProgress(c), failed: 0 };
  return {
    installed: (c.vehiclesInstalled / c.totalVehiclesTargeted) * 100,
    failed: (c.vehiclesFailed / c.totalVehiclesTargeted) * 100,
  };
}
