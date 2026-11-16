import { Link } from 'react-router-dom';
import { CloudUpload, Plus } from 'lucide-react';
import { otaApi, vehicleApi } from '../api/endpoints';
import type { Campaign, Vehicle } from '../api/types';
import { ButtonLink } from '../components/Button';
import EmptyState from '../components/EmptyState';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import ProgressBar from '../components/ProgressBar';
import StageTrack from '../components/StageTrack';
import Stat from '../components/Stat';
import Status from '../components/Status';
import { useAuth } from '../context/auth';
import { useNotifications } from '../context/notifications';
import { useAsync } from '../hooks/useAsync';
import { campaignTone, clock, connectivityTone, label, notificationTone, num, timeAgo } from '../utils/format';
import styles from './DashboardPage.module.css';

const LOW_BATTERY = 20;

function ActiveRollout({ campaign: c }: { campaign: Campaign }) {
  const share = (n: number) => (c.totalVehiclesTargeted ? (n / c.totalVehiclesTargeted) * 100 : 0);
  return (
    <li className={styles.rollout}>
      <div className={styles.rolloutHead}>
        <Link to={`/rollouts/${c.id}`} className={styles.rolloutName}>
          <span className="mono">{c.softwareVersion.versionLabel}</span>
          <span className="muted"> to </span>
          {c.targetModel}
        </Link>
        <Status tone={campaignTone[c.status]}>{label(c.status)}</Status>
      </div>
      <StageTrack stages={c.stages} currentStageIndex={c.currentStageIndex} campaignStatus={c.status} />
      <div className={styles.progressRow}>
        <ProgressBar value={share(c.vehiclesInstalled)} failed={share(c.vehiclesFailed)} size="sm"
                     label={`Rollout ${c.softwareVersion.versionLabel} progress`} />
        <span className="mono muted">{c.vehiclesInstalled} of {c.totalVehiclesTargeted} installed</span>
      </div>
    </li>
  );
}

/** The owner's vehicles at a glance; their main job on this page. */
function MyVehicles({ vehicles }: { vehicles: Vehicle[] }) {
  return (
    <Panel title="My vehicles" aside={<Link to="/vehicles">Open the list</Link>} flush>
      <table className="table">
        <thead>
          <tr><th>Vehicle</th><th>Status</th><th className="num">Battery</th><th>Last seen</th></tr>
        </thead>
        <tbody>
          {vehicles.map(v => (
            <tr key={v.id}>
              <td><Link to={`/vehicles/${v.id}`} className="row-link">{v.model}</Link> <span className="muted mono">{v.modelYear}</span></td>
              <td><Status tone={connectivityTone[v.connectivityState]}>{label(v.connectivityState)}</Status></td>
              <td className="mono num">{num(v.batteryOrFuelLevel, 0, '%')}</td>
              <td className="muted">{timeAgo(v.lastSeenAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Panel>
  );
}

export default function DashboardPage() {
  const { isAdmin, user } = useAuth();
  const { notifications } = useNotifications();
  const vehicles = useAsync(isAdmin ? vehicleApi.fleet : vehicleApi.mine, [isAdmin], 15000);
  const campaigns = useAsync<Campaign[]>(() => (isAdmin ? otaApi.campaigns() : Promise.resolve([])), [isAdmin], 15000);

  const list = vehicles.data ?? [];
  const online = list.filter(v => v.connectivityState === 'ONLINE').length;
  const offline = list.filter(v => v.connectivityState === 'OFFLINE').length;
  const sleeping = list.filter(v => v.connectivityState === 'SLEEPING').length;
  const batteries = list.map(v => v.batteryOrFuelLevel).filter((b): b is number => b !== null);
  const avgBattery = batteries.length ? batteries.reduce((a, b) => a + b, 0) / batteries.length : null;
  const lowBattery = batteries.filter(b => b < LOW_BATTERY).length;
  const active = (campaigns.data ?? []).filter(c => c.status === 'IN_PROGRESS' || c.status === 'PAUSED');
  const loading = vehicles.loading && !vehicles.data;
  const firstName = user?.fullName.split(' ')[0] ?? '';

  return (
    <div className={styles.page}>
      <PageHeader
        title={isAdmin ? 'Fleet overview' : `Hello, ${firstName}`}
        meta={isAdmin ? 'Everything on the road right now.' : 'Your vehicles at a glance.'}
      />
      {vehicles.error && <p className="error" role="alert">{vehicles.error}</p>}

      <section className={styles.summary} aria-label="Summary">
        <Stat mono size="lg" label={isAdmin ? 'Vehicles' : 'My vehicles'} value={loading ? '–' : list.length} />
        <Stat mono size="lg" label="Online" value={loading ? '–' : online}
              detail={list.length ? `${Math.round((online / list.length) * 100)}% of ${isAdmin ? 'the fleet' : 'your vehicles'}` : undefined} />
        <Stat mono size="lg" label="Average battery" value={num(avgBattery, 0, '%')}
              detail={lowBattery ? `${lowBattery} below ${LOW_BATTERY}%` : batteries.length ? 'None running low' : undefined} />
        {isAdmin && <Stat mono size="lg" label="Active rollouts" value={campaigns.loading && !campaigns.data ? '–' : active.length} />}
      </section>

      <div className={styles.grid}>
        <div className={styles.column}>
          {!isAdmin && list.length > 0 && <MyVehicles vehicles={list} />}
          {isAdmin && (
            <Panel title="Active rollouts" aside={<Link to="/rollouts">All rollouts</Link>}>
              {active.length === 0 ? (
                <EmptyState inset={false}>No rollouts running. Start one from OTA updates.</EmptyState>
              ) : (
                <ul className={styles.rollouts}>{active.map(c => <ActiveRollout key={c.id} campaign={c} />)}</ul>
              )}
            </Panel>
          )}
          <Panel title="Live activity" aside={notifications.length ? `${notifications.length} since you signed in` : undefined} flush>
            {notifications.length === 0 ? (
              <EmptyState>
                Quiet for now. Command results{isAdmin ? ' and finished rollouts' : ''} appear here as they happen.
              </EmptyState>
            ) : (
              <ol className={styles.log}>
                {notifications.slice(0, 8).map(n => (
                  <li key={n.id}>
                    <time className="mono muted" dateTime={n.timestamp.toISOString()}>{clock(n.timestamp)}</time>
                    <Status tone={notificationTone[n.type]}>{n.message}</Status>
                  </li>
                ))}
              </ol>
            )}
          </Panel>
        </div>

        <div className={styles.column}>
          <Panel title="Connectivity">
            <ul className={styles.breakdown}>
              <li><Status tone="ok">Online</Status><span className="mono">{online}</span></li>
              <li><Status tone="warn">Sleeping</Status><span className="mono">{sleeping}</span></li>
              <li><Status tone="bad">Offline</Status><span className="mono">{offline}</span></li>
            </ul>
          </Panel>
          <Panel title="Shortcuts">
            <div className={styles.actions}>
              <ButtonLink to="/vehicles?register=1" icon={Plus}>Register a vehicle</ButtonLink>
              {isAdmin && <ButtonLink to="/rollouts" icon={CloudUpload}>Plan a rollout</ButtonLink>}
            </div>
          </Panel>
        </div>
      </div>
    </div>
  );
}
