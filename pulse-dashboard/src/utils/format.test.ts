import { describe, expect, it } from 'vitest';
import type { Campaign } from '../api/types';
import { campaignBar, campaignProgress, failureRate, label } from './format';

function campaign(overrides: Partial<Campaign>): Campaign {
  return {
    status: 'IN_PROGRESS',
    totalVehiclesTargeted: 0,
    vehiclesInstalled: 0,
    vehiclesFailed: 0,
    vehiclesTimedOut: 0,
    ...overrides,
  } as Campaign;
}

describe('campaignProgress', () => {
  it('is the share of targeted vehicles that finished, installed or failed', () => {
    expect(campaignProgress(campaign({ totalVehiclesTargeted: 8, vehiclesInstalled: 2, vehiclesFailed: 1 }))).toBe(38);
  });

  it('counts timed-out vehicles as finished', () => {
    expect(campaignProgress(campaign({ totalVehiclesTargeted: 10, vehiclesInstalled: 5, vehiclesFailed: 1, vehiclesTimedOut: 4 }))).toBe(100);
  });

  it('is 100 for a completed campaign that targeted no vehicles', () => {
    expect(campaignProgress(campaign({ totalVehiclesTargeted: 0, status: 'COMPLETED' }))).toBe(100);
  });

  it('is 0 for a running campaign that has targeted no vehicles yet', () => {
    expect(campaignProgress(campaign({ totalVehiclesTargeted: 0, status: 'IN_PROGRESS' }))).toBe(0);
  });
});

describe('label', () => {
  it('turns an enum value into sentence case', () => {
    expect(label('IN_PROGRESS')).toBe('In progress');
    expect(label('FLEET_ADMIN')).toBe('Fleet admin');
    expect(label('LOCK')).toBe('Lock');
  });
});

describe('campaignBar', () => {
  it('splits progress into installed and failed shares of the targeted vehicles', () => {
    expect(campaignBar(campaign({ totalVehiclesTargeted: 12, vehiclesInstalled: 9, vehiclesFailed: 3 }))).toEqual({ installed: 75, failed: 25 });
  });

  it('shows a completed campaign that targeted nobody as fully installed', () => {
    expect(campaignBar(campaign({ totalVehiclesTargeted: 0, status: 'COMPLETED' }))).toEqual({ installed: 100, failed: 0 });
  });
});

describe('failureRate', () => {
  it('rates failures against the vehicles that did not time out, as the server does', () => {
    // 1 failed of the 6 that reported or are still running; the 4 timed out are left out.
    expect(failureRate(campaign({ totalVehiclesTargeted: 10, vehiclesInstalled: 5, vehiclesFailed: 1, vehiclesTimedOut: 4 })))
      .toBeCloseTo(16.67, 2);
  });

  it('is 0 when every vehicle timed out, or none was targeted', () => {
    expect(failureRate(campaign({ totalVehiclesTargeted: 3, vehiclesTimedOut: 3 }))).toBe(0);
    expect(failureRate(campaign({ totalVehiclesTargeted: 0 }))).toBe(0);
  });
});
