import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { Pause, Play, Square, type LucideIcon } from 'lucide-react';
import BackLink from '../components/BackLink';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import ProgressBar from '../components/ProgressBar';
import StageTrack from '../components/StageTrack';
import Stat from '../components/Stat';
import Status from '../components/Status';
import { otaApi } from '../api/endpoints';
import type { Campaign, CampaignStatus } from '../api/types';
import { useTopic } from '../context/live';
import { useAsync } from '../hooks/useAsync';
import { campaignBar, campaignProgress, campaignTone, dateTime, failureRate, label, updateTone } from '../utils/format';
import { mergeLoaded, newer } from '../utils/versions';
import styles from './RolloutDetailPage.module.css';

type Action = 'start' | 'pause' | 'resume' | 'abort';

const ACTIONS: Record<CampaignStatus, Action[]> = {
  DRAFT: ['start', 'abort'],
  IN_PROGRESS: ['pause', 'abort'],
  PAUSED: ['resume', 'abort'],
  COMPLETED: [],
  ABORTED: [],
};

const ACTION_UI: Record<Action, { text: string; icon: LucideIcon; variant: 'primary' | 'secondary' | 'danger' }> = {
  start: { text: 'Start', icon: Play, variant: 'primary' },
  pause: { text: 'Pause', icon: Pause, variant: 'secondary' },
  resume: { text: 'Resume', icon: Play, variant: 'primary' },
  abort: { text: 'Abort', icon: Square, variant: 'danger' },
};

export default function RolloutDetailPage() {
  const { id = '' } = useParams();
  const campaign = useAsync(() => otaApi.campaign(id), [id], { merge: newer });
  const vehicles = useAsync(() => otaApi.campaignVehicles(id), [id], { merge: mergeLoaded });
  const [busy, setBusy] = useState<Action | null>(null);
  const [error, setError] = useState('');

  useTopic<Campaign>(`/topic/rollouts/${id}`, update => {
    campaign.setData(shown => newer(shown, update));
    vehicles.reload();
  });

  const run = async (action: Action) => {
    setBusy(action);
    setError('');
    try {
      const response = await otaApi.action(id, action);
      campaign.setData(shown => newer(shown, response));
      vehicles.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : `Could not ${action} the rollout`);
    } finally {
      setBusy(null);
    }
  };

  const c = campaign.data;
  if (campaign.error) return <p className="error" role="alert">{campaign.error}</p>;
  if (!c) return <p className="muted">Loading rollout…</p>;

  const rate = failureRate(c);
  const overThreshold = rate > c.failureThresholdPercent;
  const progress = campaignProgress(c);

  return (
    <div>
      <BackLink to="/rollouts">OTA updates</BackLink>
      <PageHeader
        eyebrow="Rollout"
        title={<><span className="mono">{c.softwareVersion.versionLabel}</span> <span className="muted">to</span> {c.targetModel}</>}
        meta={<>
          <Status tone={campaignTone[c.status]}>{label(c.status)}</Status>
          <span>Created {dateTime(c.createdAt)}</span>
          <span>Pauses above {c.failureThresholdPercent}% failed per stage</span>
        </>}
        actions={ACTIONS[c.status].map(action => {
          const ui = ACTION_UI[action];
          return (
            <Button key={action} variant={ui.variant} icon={ui.icon} isLoading={busy === action}
                    disabled={busy !== null} onClick={() => run(action)}>
              {ui.text}
            </Button>
          );
        })}
      />
      {error && <p className="error" role="alert">{error}</p>}

      <Panel className={styles.progressPanel}>
        <div className={styles.progress}>
          <StageTrack stages={c.stages} currentStageIndex={c.currentStageIndex} campaignStatus={c.status} />
          <div className={styles.bar}>
            <ProgressBar value={campaignBar(c).installed} failed={campaignBar(c).failed} label="Rollout progress"
                         tone={c.status === 'COMPLETED' ? 'ok' : 'accent'} />
            <span className="mono">{progress}% finished</span>
          </div>
          <div className={styles.figures}>
            <Stat mono label="Installed" value={c.vehiclesInstalled} />
            <Stat mono label="Failed" value={c.vehiclesFailed} />
            <Stat mono label="Timed out" value={c.vehiclesTimedOut ?? 0} detail="Not counted as failed" />
            <Stat mono label="Targeted" value={c.totalVehiclesTargeted} />
            <Stat mono label="Failure rate" value={<span className={overThreshold ? 'text-bad' : ''}>{rate.toFixed(1)}%</span>}
                  detail={overThreshold ? `Above the ${c.failureThresholdPercent}% limit` : `Limit ${c.failureThresholdPercent}%`} />
          </div>
        </div>
      </Panel>

      <Panel title="Vehicles" aside={vehicles.data?.length ? <span className="mono">{vehicles.data.length}</span> : undefined} flush>
        {vehicles.data?.length === 0 ? (
          <EmptyState>No vehicles targeted yet. Starting the rollout instructs the first stage.</EmptyState>
        ) : (
          <table className="table">
            <thead>
              <tr><th>VIN</th><th>Status</th><th className="num">Progress</th><th>Started</th><th>Error</th></tr>
            </thead>
            <tbody>
              {vehicles.data?.map(u => (
                <tr key={u.id}>
                  <td className="mono">{u.vin}</td>
                  <td><Status tone={updateTone[u.status]}>{label(u.status)}</Status></td>
                  <td className="mono num">{u.progressPercent}%</td>
                  <td className="mono muted">{dateTime(u.startedAt)}</td>
                  <td className="wrap text-bad">{u.errorMessage ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}
