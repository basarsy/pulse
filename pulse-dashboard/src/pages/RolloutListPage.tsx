import React from 'react';
import Badge from '../components/Badge';

export default function RolloutListPage() {
  const rollouts = [
    { id: '1', version: 'v2024.12.3', target: 'Model S', status: 'IN_PROGRESS', progress: 45 },
    { id: '2', version: 'v2024.12.1', target: 'Model 3', status: 'COMPLETED', progress: 100 },
    { id: '3', version: 'v2024.12.4', target: 'Model X', status: 'DRAFT', progress: 0 },
  ];

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED': return 'green';
      case 'IN_PROGRESS': return 'blue';
      case 'PAUSED': return 'yellow';
      case 'ABORTED': return 'red';
      default: return 'gray';
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h2>OTA Campaigns</h2>
        <button className="form-input" style={{ width: 'auto', background: 'var(--color-accent-primary)', color: 'white', border: 'none' }}>+ New Rollout</button>
      </div>

      <div className="glass-panel" style={{ overflow: 'hidden' }}>
        <table>
          <thead>
            <tr>
              <th>Version</th>
              <th>Target Model</th>
              <th>Status</th>
              <th>Progress</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {rollouts.map(r => (
              <tr key={r.id}>
                <td style={{ fontWeight: 600 }}>{r.version}</td>
                <td>{r.target}</td>
                <td><Badge color={getStatusColor(r.status)}>{r.status}</Badge></td>
                <td>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                    <div style={{ flex: 1, height: '8px', background: 'rgba(255,255,255,0.1)', borderRadius: '4px', overflow: 'hidden' }}>
                      <div style={{ width: `${r.progress}%`, height: '100%', background: r.status === 'COMPLETED' ? 'var(--color-success)' : 'var(--color-accent-primary)' }}></div>
                    </div>
                    <span style={{ fontSize: '0.875rem' }}>{r.progress}%</span>
                  </div>
                </td>
                <td>
                  <a href={`/rollouts/${r.id}`} style={{ fontSize: '0.875rem' }}>View Details</a>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
