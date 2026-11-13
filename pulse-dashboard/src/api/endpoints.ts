import { http } from './client';
import type {
  AuthResponse, Campaign, Command, CommandType, SoftwareVersion, TelemetrySnapshot, User, Vehicle, VehicleUpdate,
} from './types';

export const authApi = {
  login: (email: string, password: string) => http.post<AuthResponse>('/auth/login', { email, password }),
  register: (fullName: string, email: string, password: string) =>
    http.post<AuthResponse>('/auth/register', { fullName, email, password }),
  me: () => http.get<User>('/users/me'),
};

export const vehicleApi = {
  mine: () => http.get<Vehicle[]>('/vehicles'),
  fleet: () => http.get<Vehicle[]>('/fleet/vehicles'),
  get: (id: string) => http.get<Vehicle>(`/vehicles/${id}`),
  register: (vin: string, model: string, modelYear: number) =>
    http.post<Vehicle>('/vehicles', { vin, model, modelYear }),
  commands: (id: string) => http.get<Command[]>(`/vehicles/${id}/commands`),
  issueCommand: (id: string, type: CommandType) =>
    http.post<Command>(`/vehicles/${id}/commands`, { type, idempotencyKey: crypto.randomUUID() }),
  cancelCommand: (id: string, commandId: string) => http.delete<Command>(`/vehicles/${id}/commands/${commandId}`),
  telemetryHistory: (id: string, size = 20) =>
    http.get<TelemetrySnapshot[]>(`/vehicles/${id}/telemetry/history?page=0&size=${size}`),
  updates: (id: string) => http.get<VehicleUpdate[]>(`/vehicles/${id}/update-status`),
};

export const otaApi = {
  versions: () => http.get<SoftwareVersion[]>('/software-versions'),
  createVersion: (versionLabel: string, packageUrl: string, checksum: string, releaseNotes: string) =>
    http.post<SoftwareVersion>('/software-versions', { versionLabel, packageUrl, checksum, releaseNotes }),
  campaigns: () => http.get<Campaign[]>('/rollouts'),
  campaign: (id: string) => http.get<Campaign>(`/rollouts/${id}`),
  campaignVehicles: (id: string) => http.get<VehicleUpdate[]>(`/rollouts/${id}/vehicles`),
  createCampaign: (softwareVersionId: string, targetModel: string, stagePercentages: number[], failureThresholdPercent: number) =>
    http.post<Campaign>('/rollouts', { softwareVersionId, targetModel, stagePercentages, failureThresholdPercent }),
  action: (id: string, action: 'start' | 'pause' | 'resume' | 'abort') => http.post<Campaign>(`/rollouts/${id}/${action}`),
};
