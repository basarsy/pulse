import React from 'react';
import { api } from '../api/client';
import StatCard from '../components/StatCard';

export default function DashboardPage() {
  // In a real app, fetch these from an API
  return (
    <div>
      <h2>Dashboard Overview</h2>
      <div className="stat-grid">
        <StatCard title="Total Vehicles" value="1,248" icon="🚗" trend="+12 this week" />
        <StatCard title="Online Vehicles" value="842" icon="🟢" trend="67% online" />
        <StatCard title="Active Commands" value="15" icon="⚡" />
        <StatCard title="Active Rollouts" value="2" icon="☁️" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '1.5rem' }}>
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <h3>Recent Activity Feed</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1rem' }}>
            <div style={{ padding: '1rem', background: 'rgba(255,255,255,0.02)', borderRadius: 'var(--radius-md)' }}>
              <strong>Update v2.4.1</strong> rollout reached 50% on Model S
            </div>
            <div style={{ padding: '1rem', background: 'rgba(255,255,255,0.02)', borderRadius: 'var(--radius-md)' }}>
              Vehicle <strong>VIN...7890</strong> connected
            </div>
          </div>
        </div>

        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <h3>Quick Actions</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1rem' }}>
            <button className="form-input" style={{ textAlign: 'left', cursor: 'pointer' }}>➕ Register Vehicle</button>
            <button className="form-input" style={{ textAlign: 'left', cursor: 'pointer' }}>🚀 New Rollout</button>
          </div>
        </div>
      </div>
    </div>
  );
}
