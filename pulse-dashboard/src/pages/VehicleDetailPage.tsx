import { useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Activity, Fan, Lightbulb, Lock, LockOpen, MapPin, Power, type LucideIcon,
} from 'lucide-react';
import BackLink from '../components/BackLink';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import Stat from '../components/Stat';
import Status from '../components/Status';
import { vehicleApi } from '../api/endpoints';
import { COMMAND_TYPES, type Command, type CommandType, type TelemetrySnapshot, type VehicleUpdate } from '../api/types';
import { useTopic } from '../context/live';
import { useAsync } from '../hooks/useAsync';
import { commandTone, connectivityTone, dateTime, label, num, timeAgo, updateTone, upsertById } from '../utils/format';
import styles from './VehicleDetailPage.module.css';

const TABS = ['overview', 'commands', 'telemetry', 'updates'] as const;
type Tab = typeof TABS[number];

const COMMAND_ICON: Record<CommandType, LucideIcon> = {
  LOCK: Lock,
  UNLOCK: LockOpen,
  REMOTE_START: Power,
  FLASH_LIGHTS: Lightbulb,
  LOCATE: MapPin,
  CLIMATE_ON: Fan,
  CHECK_STATUS: Activity,
};

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
      setError(err instanceof Error ? err.message : 'The command could not be sent');
    } finally {
      setSending(null);
    }
  };

  const cancel = async (commandId: string) => {
    try {
      const command = await vehicleApi.cancelCommand(vehicleId, commandId);
      commands.setData(list => upsertById(list, command));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The command could not be cancelled');
    }
  };

  return (
    <div className={styles.stack}>
      <Panel title="Send a command" aside="Queued if the vehicle is offline">
        <div className={styles.commands}>
          {COMMAND_TYPES.map(type => (
            <Button key={type} variant="secondary" icon={COMMAND_ICON[type]} isLoading={sending === type}
                    disabled={sending !== null} onClick={() => issue(type)}>
              {label(type)}
            </Button>
          ))}
        </div>
        {error && <p className={`error ${styles.error}`} role="alert">{error}</p>}
      </Panel>
      <Panel title="History" flush>
        {commands.data?.length === 0 ? (
          <EmptyState>No commands sent to this vehicle yet.</EmptyState>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Command</th><th>Status</th><th>Requested</th><th>Result</th><th aria-label="Actions" /></tr>
            </thead>
            <tbody>
              {commands.data?.map(c => (
                <tr key={c.id}>
                  <td>{label(c.type)}</td>
                  <td><Status tone={commandTone[c.status]}>{label(c.status)}</Status></td>
                  <td className="muted">{timeAgo(c.requestedAt)}</td>
                  <td className="muted wrap">{c.resultMessage ?? '—'}</td>
                  <td className="num">
                    {(c.status === 'PENDING' || c.status === 'SENT') && (
                      <Button variant="ghost" size="sm" onClick={() => cancel(c.id)}>Cancel</Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}

function TelemetryTab({ vehicleId }: { vehicleId: string }) {
  const history = useAsync<TelemetrySnapshot[]>(() => vehicleApi.telemetryHistory(vehicleId), [vehicleId], 20000);
  return (
    <Panel title="Telemetry" aside="Newest first" flush>
      {history.data?.length === 0 ? (
        <EmptyState>No telemetry yet. Readings appear once the vehicle reports in.</EmptyState>
      ) : (
        <table className="table">
          <thead>
            <tr><th>Recorded</th><th className="num">Battery / fuel</th><th className="num">Odometer</th><th className="num">Engine temp</th><th>Position</th></tr>
          </thead>
          <tbody>
            {history.data?.map(t => (
              <tr key={t.id}>
                <td className="mono muted">{dateTime(t.recordedAt)}</td>
                <td className="mono num">{num(t.batteryOrFuelLevel, 1, '%')}</td>
                <td className="mono num">{num(t.odometerKm, 1, ' km')}</td>
                <td className="mono num">{num(t.engineTempC, 1, ' °C')}</td>
                <td className="mono">{num(t.lat, 4)}, {num(t.lng, 4)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Panel>
  );
}

function UpdatesTab({ vehicleId }: { vehicleId: string }) {
  const updates = useAsync<VehicleUpdate[]>(() => vehicleApi.updates(vehicleId), [vehicleId]);
  useTopic<VehicleUpdate>(`/topic/vehicles/${vehicleId}/update-status`, update =>
    updates.setData(list => upsertById(list, update)));

  return (
    <Panel title="Software updates" flush>
      {updates.data?.length === 0 ? (
        <EmptyState>No software updates for this vehicle yet.</EmptyState>
      ) : (
        <table className="table">
          <thead>
            <tr><th>Version</th><th>Status</th><th className="num">Progress</th><th>Started</th><th>Completed</th><th>Error</th></tr>
          </thead>
          <tbody>
            {updates.data?.map(u => (
              <tr key={u.id}>
                <td className="mono">{u.targetVersionLabel}</td>
                <td><Status tone={updateTone[u.status]}>{label(u.status)}</Status></td>
                <td className="mono num">{u.progressPercent}%</td>
                <td className="mono muted">{dateTime(u.startedAt)}</td>
                <td className="mono muted">{dateTime(u.completedAt)}</td>
                <td className="wrap text-bad">{u.errorMessage ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Panel>
  );
}

export default function VehicleDetailPage() {
  const { id = '' } = useParams();
  const [tab, setTab] = useState<Tab>('overview');
  const { data: vehicle, error } = useAsync(() => vehicleApi.get(id), [id], 15000);

  if (error) return <p className="error" role="alert">{error}</p>;
  if (!vehicle) return <p className="muted">Loading vehicle…</p>;

  const position = vehicle.lastLat !== null ? `${num(vehicle.lastLat, 4)}, ${num(vehicle.lastLng, 4)}` : '—';

  return (
    <div>
      <BackLink to="/vehicles">Vehicles</BackLink>
      <PageHeader
        title={<>{vehicle.model} <span className="muted mono">{vehicle.modelYear}</span></>}
        meta={<>
          <span className="mono">{vehicle.vin}</span>
          <Status tone={connectivityTone[vehicle.connectivityState]}>{label(vehicle.connectivityState)}</Status>
        </>}
      />

      <section className={styles.readouts} aria-label="Latest readings">
        <Stat mono label="Battery / fuel" value={num(vehicle.batteryOrFuelLevel, 1, '%')} />
        <Stat mono label="Odometer" value={num(vehicle.odometerKm, 0, ' km')} />
        <Stat mono label="Software" value={vehicle.currentSoftwareVersion ?? '—'} />
        <Stat label="Last seen" value={timeAgo(vehicle.lastSeenAt)} />
        <Stat mono label="Position" value={position} />
      </section>

      <div className={styles.tabs} role="group" aria-label="Vehicle sections">
        {TABS.map(t => (
          <button key={t} aria-pressed={tab === t}
                  className={`${styles.tab} ${tab === t ? styles.selected : ''}`} onClick={() => setTab(t)}>
            {label(t)}
          </button>
        ))}
      </div>

      <div>
        {tab === 'overview' && (
          <Panel title="Details">
            <div className={styles.details}>
              <Stat label="Owner" value={vehicle.ownerEmail} />
              <Stat label="Registered" value={dateTime(vehicle.createdAt)} />
            </div>
          </Panel>
        )}
        {tab === 'commands' && <CommandsTab vehicleId={vehicle.id} />}
        {tab === 'telemetry' && <TelemetryTab vehicleId={vehicle.id} />}
        {tab === 'updates' && <UpdatesTab vehicleId={vehicle.id} />}
      </div>
    </div>
  );
}
