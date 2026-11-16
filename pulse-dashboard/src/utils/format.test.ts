import { describe, expect, it } from 'vitest';
import type { Campaign } from '../api/types';
import { campaignProgress, label } from './format';

function campaign(overrides: Partial<Campaign>): Campaign {
  return {
    status: 'IN_PROGRESS',
    totalVehiclesTargeted: 0,
    vehiclesInstalled: 0,
    vehiclesFailed: 0,
    ...overrides,
  } as Campaign;
}

describe('campaignProgress', () => {
  it('is the share of targeted vehicles that finished, installed or failed', () => {
    expect(campaignProgress(campaign({ totalVehiclesTargeted: 8, vehiclesInstalled: 2, vehiclesFailed: 1 }))).toBe(38);
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
