import { Link } from 'react-router-dom';
import StatCard from '../components/StatCard';
import { otaApi, vehicleApi } from '../api/endpoints';
import type { Campaign } from '../api/types';
import { useAuth } from '../context/AuthContext';
import { useNotifications } from '../context/NotificationContext';
import { useAsync } from '../hooks/useAsync';
import { num } from '../utils/format';

export default function DashboardPage() {
  const { isAdmin, user } = useAuth();
  const { notifications } = useNotifications();
  const vehicles = useAsync(isAdmin ? vehicleApi.fleet : vehicleApi.mine, [isAdmin], 15000);
  const campaigns = useAsync<Campaign[]>(() => (isAdmin ? otaApi.campaigns() : Promise.resolve([])), [isAdmin], 15000);

  const list = vehicles.data ?? [];
  const online = list.filter(v => v.connectivityState === 'ONLINE').length;
  const batteries = list.map(v => v.batteryOrFuelLevel).filter((b): b is number => b !== null);
  const avgBattery = batteries.length ? batteries.reduce((a, b) => a + b, 0) / batteries.length : null;
  const activeRollouts = (campaigns.data ?? []).filter(c => c.status === 'IN_PROGRESS' || c.status === 'PAUSED').length;

  return (
    <div>
      <h2>{isAdmin ? 'Fleet Overview' : `Welcome, ${user?.fullName ?? ''}`}</h2>
      {vehicles.error && <div style={{ color: 'var(--color-danger)' }}>{vehicles.error}</div>}

      <div className="stat-grid">
        <StatCard title={isAdmin ? 'Fleet Vehicles' : 'My Vehicles'} value={vehicles.loading ? '…' : list.length} icon="🚗" />
        <StatCard title="Online Now" value={vehicles.loading ? '…' : online}
                  icon="🟢" trend={list.length ? `${Math.round((online / list.length) * 100)}% online` : undefined} />
        <StatCard title="Average Battery" value={num(avgBattery, 0, '%')} icon="🔋" />
        {isAdmin && <StatCard title="Active Rollouts" value={campaigns.loading ? '…' : activeRollouts} icon="☁️" />}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '1.5rem' }}>
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <h3>Live Activity</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '1rem' }}>
            {notifications.length === 0 && (
              <div style={{ color: 'var(--color-text-secondary)' }}>
                No activity yet. Command results{isAdmin ? ' and rollout changes' : ''} appear here as they happen.
              </div>
            )}
            {notifications.slice(0, 8).map(n => (
              <div key={n.id} style={{ padding: '0.75rem 1rem', background: 'rgba(255,255,255,0.02)', borderRadius: 'var(--radius-md)', display: 'flex', justifyContent: 'space-between' }}>
                <span>{n.message}</span>
                <span style={{ color: 'var(--color-text-secondary)', fontSize: '0.75rem' }}>{n.timestamp.toLocaleTimeString()}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <h3>Quick Actions</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1rem' }}>
            <Link to="/vehicles" className="form-input" style={{ textAlign: 'left' }}>➕ Register a vehicle</Link>
            {isAdmin && <Link to="/rollouts" className="form-input" style={{ textAlign: 'left' }}>🚀 Plan a rollout</Link>}
          </div>
        </div>
      </div>
    </div>
  );
}
