import { useState } from 'react';
import { useParams } from 'react-router-dom';
import Badge from '../components/Badge';
import Button from '../components/Button';
import { otaApi } from '../api/endpoints';
import type { Campaign, CampaignStatus } from '../api/types';
import { useTopic } from '../context/LiveContext';
import { useAsync } from '../hooks/useAsync';
import { campaignColor, dateTime, label, updateColor } from '../utils/format';
import { campaignProgress } from './RolloutListPage';

type Action = 'start' | 'pause' | 'resume' | 'abort';

const ACTIONS: Record<CampaignStatus, Action[]> = {
  DRAFT: ['start', 'abort'],
  IN_PROGRESS: ['pause', 'abort'],
  PAUSED: ['resume', 'abort'],
  COMPLETED: [],
  ABORTED: [],
};

export default function RolloutDetailPage() {
  const { id = '' } = useParams();
  const campaign = useAsync(() => otaApi.campaign(id), [id]);
  const vehicles = useAsync(() => otaApi.campaignVehicles(id), [id]);
  const [busy, setBusy] = useState<Action | null>(null);
  const [error, setError] = useState('');

  useTopic<Campaign>(`/topic/rollouts/${id}`, update => {
    campaign.setData(update);
    vehicles.reload();
  });

  const run = async (action: Action) => {
    setBusy(action);
    setError('');
    try {
      campaign.setData(await otaApi.action(id, action));
      vehicles.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : `Could not ${action} rollout`);
    } finally {
      setBusy(null);
    }
  };

  const c = campaign.data;
  if (campaign.error) return <div style={{ color: 'var(--color-danger)' }}>{campaign.error}</div>;
  if (!c) return <div>Loading rollout…</div>;

  const failureRate = c.totalVehiclesTargeted ? (c.vehiclesFailed / c.totalVehiclesTargeted) * 100 : 0;
  const progress = campaignProgress(c);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '2rem' }}>
        <div>
          <h2>Rollout {c.softwareVersion.versionLabel}</h2>
          <div style={{ color: 'var(--color-text-secondary)' }}>
            Target: {c.targetModel} • Created {dateTime(c.createdAt)} • Pauses above {c.failureThresholdPercent}% failures per stage
          </div>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
          <Badge color={campaignColor[c.status]}>{label(c.status)}</Badge>
          {ACTIONS[c.status].map(action => (
            <Button key={action} size="sm" variant={action === 'abort' ? 'danger' : 'primary'}
                    isLoading={busy === action} disabled={busy !== null} onClick={() => run(action)}>
              {action[0].toUpperCase() + action.slice(1)}
            </Button>
          ))}
        </div>
      </div>
      {error && <div style={{ color: 'var(--color-danger)', marginBottom: '1rem' }}>{error}</div>}

      <div className="glass-panel" style={{ padding: '2rem', marginBottom: '2rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '2rem' }}>
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem', fontSize: '0.875rem' }}>
              <span>{c.vehiclesInstalled} installed • {c.vehiclesFailed} failed • {c.totalVehiclesTargeted} targeted</span>
              <span>{progress}% finished</span>
            </div>
            <div style={{ height: '12px', background: 'rgba(255,255,255,0.1)', borderRadius: '6px', overflow: 'hidden' }}>
              <div style={{ width: `${progress}%`, height: '100%', background: 'var(--color-accent-gradient)', transition: 'width 1s ease' }} />
            </div>
          </div>
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '2rem', fontWeight: 700, color: failureRate > c.failureThresholdPercent ? 'var(--color-danger)' : 'var(--color-success)' }}>
              {failureRate.toFixed(1)}%
            </div>
            <div style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>Failure rate</div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '1rem', marginTop: '1.5rem' }}>
          {c.stages.map((stage, index) => (
            <div key={stage.id} style={{
              flex: 1, padding: '0.75rem 1rem', borderRadius: 'var(--radius-md)', background: 'rgba(255,255,255,0.03)',
              border: index === c.currentStageIndex && c.status === 'IN_PROGRESS' ? '1px solid var(--color-accent-primary)' : '1px solid transparent'
            }}>
              <div style={{ fontWeight: 600 }}>Stage {index + 1} · {stage.percentage}%</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>{label(stage.status)}</div>
            </div>
          ))}
        </div>
      </div>

      <div className="glass-panel" style={{ overflow: 'hidden' }}>
        <table>
          <thead>
            <tr><th>VIN</th><th>Status</th><th>Progress</th><th>Started</th><th>Error</th></tr>
          </thead>
          <tbody>
            {vehicles.data?.length === 0 && (
              <tr><td colSpan={5} style={{ color: 'var(--color-text-secondary)' }}>No vehicles targeted yet.</td></tr>
            )}
            {vehicles.data?.map(u => (
              <tr key={u.id}>
                <td style={{ fontFamily: 'monospace' }}>{u.vin}</td>
                <td><Badge color={updateColor[u.status]}>{label(u.status)}</Badge></td>
                <td>{u.progressPercent}%</td>
                <td>{dateTime(u.startedAt)}</td>
                <td style={{ color: 'var(--color-danger)' }}>{u.errorMessage ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
