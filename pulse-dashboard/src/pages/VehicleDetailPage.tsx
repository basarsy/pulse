import { useState } from 'react';
import { useParams } from 'react-router-dom';
import Badge from '../components/Badge';
import Button from '../components/Button';
import { vehicleApi } from '../api/endpoints';
import { COMMAND_TYPES, type Command, type CommandType, type TelemetrySnapshot, type VehicleUpdate } from '../api/types';
import { useTopic } from '../context/LiveContext';
import { useAsync } from '../hooks/useAsync';
import { commandColor, connectivityColor, dateTime, label, num, timeAgo, updateColor, upsertById } from '../utils/format';

const TABS = ['overview', 'commands', 'telemetry', 'updates'] as const;
type Tab = typeof TABS[number];

function Stat({ title, value }: { title: string; value: string }) {
  return (
    <div>
      <div style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary)' }}>{title}</div>
      <div style={{ fontSize: '1.25rem', fontWeight: 600 }}>{value}</div>
    </div>
  );
}

function CommandsTab({ vehicleId }: { vehicleId: string }) {
  const commands = useAsync(() => vehicleApi.commands(vehicleId), [vehicleId]);
  const [sending, setSending] = useState<CommandType | null>(null);
  const [error, setError] = useState('');

  // Live status changes (PENDING → SENT → COMPLETED …) pushed by the server.
  useTopic<Command>(`/topic/vehicles/${vehicleId}/commands`, command =>
    commands.setData(list => upsertById(list, command)));

  const issue = async (type: CommandType) => {
    setSending(type);
    setError('');
    try {
      const command = await vehicleApi.issueCommand(vehicleId, type);
      commands.setData(list => upsertById(list, command));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Command failed');
    } finally {
      setSending(null);
    }
  };

  const cancel = async (commandId: string) => {
    try {
      const command = await vehicleApi.cancelCommand(vehicleId, commandId);
      commands.setData(list => upsertById(list, command));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not cancel');
    }
  };

  return (
    <div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
        {COMMAND_TYPES.map(type => (
          <Button key={type} variant="secondary" isLoading={sending === type} disabled={sending !== null} onClick={() => issue(type)}>
            {label(type)}
          </Button>
        ))}
      </div>
      {error && <div style={{ color: 'var(--color-danger)', marginBottom: '1rem' }}>{error}</div>}

      <div className="glass-panel" style={{ overflow: 'hidden' }}>
        <table>
          <thead>
            <tr><th>Command</th><th>Status</th><th>Requested</th><th>Result</th><th /></tr>
          </thead>
          <tbody>
            {commands.data?.length === 0 && (
              <tr><td colSpan={5} style={{ color: 'var(--color-text-secondary)' }}>No commands sent yet.</td></tr>
            )}
            {commands.data?.map(c => (
              <tr key={c.id}>
                <td style={{ fontWeight: 600 }}>{label(c.type)}</td>
                <td><Badge color={commandColor[c.status]}>{label(c.status)}</Badge></td>
                <td>{timeAgo(c.requestedAt)}</td>
                <td style={{ color: 'var(--color-text-secondary)' }}>{c.resultMessage ?? '—'}</td>
                <td>
                  {(c.status === 'PENDING' || c.status === 'SENT') && (
                    <Button variant="ghost" size="sm" onClick={() => cancel(c.id)}>Cancel</Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function TelemetryTab({ vehicleId }: { vehicleId: string }) {
  const history = useAsync<TelemetrySnapshot[]>(() => vehicleApi.telemetryHistory(vehicleId), [vehicleId], 20000);
  return (
    <div className="glass-panel" style={{ overflow: 'hidden' }}>
      <table>
        <thead>
          <tr><th>Recorded</th><th>Battery / fuel</th><th>Odometer</th><th>Engine temp</th><th>Position</th></tr>
        </thead>
        <tbody>
          {history.data?.length === 0 && (
            <tr><td colSpan={5} style={{ color: 'var(--color-text-secondary)' }}>No telemetry received yet.</td></tr>
          )}
          {history.data?.map(t => (
            <tr key={t.id}>
              <td>{dateTime(t.recordedAt)}</td>
              <td>{num(t.batteryOrFuelLevel, 1, '%')}</td>
              <td>{num(t.odometerKm, 1, ' km')}</td>
              <td>{num(t.engineTempC, 1, ' °C')}</td>
              <td style={{ fontFamily: 'monospace' }}>{num(t.lat, 4)}, {num(t.lng, 4)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function UpdatesTab({ vehicleId }: { vehicleId: string }) {
  const updates = useAsync<VehicleUpdate[]>(() => vehicleApi.updates(vehicleId), [vehicleId]);
  useTopic<VehicleUpdate>(`/topic/vehicles/${vehicleId}/update-status`, update =>
    updates.setData(list => upsertById(list, update)));

  return (
    <div className="glass-panel" style={{ overflow: 'hidden' }}>
      <table>
        <thead>
          <tr><th>Version</th><th>Status</th><th>Progress</th><th>Started</th><th>Completed</th><th>Error</th></tr>
        </thead>
        <tbody>
          {updates.data?.length === 0 && (
            <tr><td colSpan={6} style={{ color: 'var(--color-text-secondary)' }}>No software updates for this vehicle yet.</td></tr>
          )}
          {updates.data?.map(u => (
            <tr key={u.id}>
              <td style={{ fontWeight: 600 }}>{u.targetVersionLabel}</td>
              <td><Badge color={updateColor[u.status]}>{label(u.status)}</Badge></td>
              <td>{u.progressPercent}%</td>
              <td>{dateTime(u.startedAt)}</td>
              <td>{dateTime(u.completedAt)}</td>
              <td style={{ color: 'var(--color-danger)' }}>{u.errorMessage ?? ''}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function VehicleDetailPage() {
  const { id = '' } = useParams();
  const [tab, setTab] = useState<Tab>('overview');
  const { data: vehicle, error } = useAsync(() => vehicleApi.get(id), [id], 15000);

  if (error) return <div style={{ color: 'var(--color-danger)' }}>{error}</div>;
  if (!vehicle) return <div>Loading vehicle…</div>;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '2rem' }}>
        <div>
          <h2>{vehicle.modelYear} {vehicle.model}</h2>
          <div style={{ color: 'var(--color-text-secondary)' }}>
            VIN: {vehicle.vin} • SW: {vehicle.currentSoftwareVersion ?? 'unknown'} • Last seen {timeAgo(vehicle.lastSeenAt)}
          </div>
        </div>
        <Badge color={connectivityColor[vehicle.connectivityState]}>{vehicle.connectivityState}</Badge>
      </div>

      <div style={{ display: 'flex', gap: '2rem', borderBottom: '1px solid var(--color-border)', marginBottom: '2rem' }}>
        {TABS.map(t => (
          <div
            key={t}
            onClick={() => setTab(t)}
            style={{
              paddingBottom: '1rem',
              cursor: 'pointer',
              textTransform: 'capitalize',
              color: tab === t ? 'var(--color-accent-primary)' : 'var(--color-text-secondary)',
              borderBottom: tab === t ? '2px solid var(--color-accent-primary)' : '2px solid transparent',
              fontWeight: tab === t ? 600 : 400
            }}
          >
            {t}
          </div>
        ))}
      </div>

      {tab === 'overview' && (
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <h4 style={{ margin: '0 0 1rem 0' }}>Status Summary</h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1.5rem' }}>
            <Stat title="Battery / fuel" value={num(vehicle.batteryOrFuelLevel, 1, '%')} />
            <Stat title="Odometer" value={num(vehicle.odometerKm, 0, ' km')} />
            <Stat title="Position" value={vehicle.lastLat !== null ? `${num(vehicle.lastLat, 4)}, ${num(vehicle.lastLng, 4)}` : '—'} />
            <Stat title="Software" value={vehicle.currentSoftwareVersion ?? 'unknown'} />
            <Stat title="Owner" value={vehicle.ownerEmail} />
            <Stat title="Registered" value={dateTime(vehicle.createdAt)} />
          </div>
        </div>
      )}
      {tab === 'commands' && <CommandsTab vehicleId={vehicle.id} />}
      {tab === 'telemetry' && <TelemetryTab vehicleId={vehicle.id} />}
      {tab === 'updates' && <UpdatesTab vehicleId={vehicle.id} />}
    </div>
  );
}
