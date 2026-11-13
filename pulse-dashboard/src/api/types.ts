// Mirrors the backend DTOs (pulse-monolith ... /dto/*Response.java).

export type Role = 'OWNER' | 'FAMILY_MEMBER' | 'DEALER_STAFF' | 'FLEET_ADMIN' | 'SYSTEM_ADMIN';
export type ConnectivityState = 'ONLINE' | 'OFFLINE' | 'SLEEPING';
export type CommandType = 'LOCK' | 'UNLOCK' | 'REMOTE_START' | 'FLASH_LIGHTS' | 'LOCATE' | 'CLIMATE_ON' | 'CHECK_STATUS';
export type CommandStatus = 'PENDING' | 'SENT' | 'ACKNOWLEDGED' | 'COMPLETED' | 'FAILED' | 'TIMED_OUT';
export type CampaignStatus = 'DRAFT' | 'IN_PROGRESS' | 'PAUSED' | 'COMPLETED' | 'ABORTED';
export type StageStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
export type UpdateStatus = 'PENDING' | 'DOWNLOADING' | 'DOWNLOADED' | 'INSTALLING' | 'INSTALLED' | 'FAILED' | 'ROLLED_BACK';

export const COMMAND_TYPES: CommandType[] = ['LOCK', 'UNLOCK', 'REMOTE_START', 'FLASH_LIGHTS', 'LOCATE', 'CLIMATE_ON', 'CHECK_STATUS'];
export const ADMIN_ROLES: Role[] = ['FLEET_ADMIN', 'SYSTEM_ADMIN'];

export interface User {
  id: string;
  email: string;
  fullName: string;
  roles: Role[];
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInMs: number;
  user: User;
}

export interface Vehicle {
  id: string;
  vin: string;
  model: string;
  modelYear: number;
  ownerId: string;
  ownerEmail: string;
  connectivityState: ConnectivityState;
  currentSoftwareVersion: string | null;
  batteryOrFuelLevel: number | null;
  odometerKm: number | null;
  lastLat: number | null;
  lastLng: number | null;
  lastSeenAt: string | null;
  createdAt: string;
}

export interface Command {
  id: string;
  vehicleId: string;
  issuedByUserId: string;
  type: CommandType;
  status: CommandStatus;
  requestedAt: string;
  resolvedAt: string | null;
  resultMessage: string | null;
}

export interface TelemetrySnapshot {
  id: string;
  vehicleId: string;
  batteryOrFuelLevel: number | null;
  odometerKm: number | null;
  engineTempC: number | null;
  lat: number | null;
  lng: number | null;
  recordedAt: string;
}

export interface SoftwareVersion {
  id: string;
  versionLabel: string;
  releaseNotes: string | null;
  checksum: string;
  packageUrl: string;
  mandatory: boolean;
  createdAt: string;
}

export interface RolloutStage {
  id: string;
  percentage: number;
  status: StageStatus;
  startedAt: string | null;
  completedAt: string | null;
}

export interface Campaign {
  id: string;
  softwareVersion: SoftwareVersion;
  targetModel: string;
  status: CampaignStatus;
  currentStageIndex: number;
  failureThresholdPercent: number;
  createdByUserId: string;
  createdAt: string;
  stages: RolloutStage[];
  totalVehiclesTargeted: number;
  vehiclesInstalled: number;
  vehiclesFailed: number;
}

export interface VehicleUpdate {
  id: string;
  vehicleId: string;
  vin: string;
  campaignId: string;
  targetVersionLabel: string;
  status: UpdateStatus;
  progressPercent: number;
  startedAt: string | null;
  completedAt: string | null;
  errorMessage: string | null;
}
