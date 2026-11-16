import { Link } from 'react-router-dom';
import { ArrowRight, CloudUpload, Plus } from 'lucide-react';
import { otaApi, vehicleApi } from '../api/endpoints';
import type { Campaign } from '../api/types';
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
import { campaignProgress, campaignTone, clock, label, notificationTone, num } from '../utils/format';
import styles from './DashboardPage.module.css';

const LOW_BATTERY = 20;

function ActiveRollout({ campaign: c }: { campaign: Campaign }) {
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
        <ProgressBar value={campaignProgress(c)} label={`Rollout ${c.softwareVersion.versionLabel} progress`} size="sm" />
        <span className="mono muted">{c.vehiclesInstalled} of {c.totalVehiclesTargeted} installed</span>
      </div>
    </li>
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
        <Stat size="lg" label={isAdmin ? 'Vehicles' : 'My vehicles'} value={loading ? '–' : list.length} />
        <Stat size="lg" label="Online" value={loading ? '–' : online}
              detail={list.length ? `${Math.round((online / list.length) * 100)}% of ${isAdmin ? 'the fleet' : 'your vehicles'}` : undefined} />
        <Stat size="lg" label="Average battery" value={num(avgBattery, 0, '%')}
              detail={lowBattery ? `${lowBattery} below ${LOW_BATTERY}%` : batteries.length ? 'None running low' : undefined} />
        {isAdmin && <Stat size="lg" label="Active rollouts" value={campaigns.loading && !campaigns.data ? '–' : active.length} />}
      </section>

      <div className={styles.grid}>
        <div className={styles.column}>
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
              <ButtonLink to="/vehicles" icon={Plus}>Register a vehicle</ButtonLink>
              {isAdmin && <ButtonLink to="/rollouts" icon={CloudUpload}>Plan a rollout</ButtonLink>}
              <ButtonLink to="/vehicles" variant="ghost" icon={ArrowRight}>{isAdmin ? 'Browse the fleet' : 'Open my vehicles'}</ButtonLink>
            </div>
          </Panel>
        </div>
      </div>
    </div>
  );
}
