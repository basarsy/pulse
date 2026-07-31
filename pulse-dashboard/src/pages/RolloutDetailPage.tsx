import React from 'react';
import { useParams } from 'react-router-dom';
import Badge from '../components/Badge';
import Button from '../components/Button';

export default function RolloutDetailPage() {
  const { id } = useParams();

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '2rem' }}>
        <div>
          <h2>Rollout v2024.12.3</h2>
          <div style={{ color: 'var(--color-text-secondary)' }}>Target: Model S • Start Time: 2024-05-10 08:00 UTC</div>
        </div>
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
          <Badge color="blue">IN PROGRESS</Badge>
          <Button variant="danger" size="sm">Abort</Button>
        </div>
      </div>

      <div className="glass-panel" style={{ padding: '2rem', marginBottom: '2rem' }}>
        <h3>Rollout Progress</h3>
        <div style={{ display: 'flex', alignItems: 'center', gap: '2rem', marginTop: '1.5rem' }}>
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem', fontSize: '0.875rem' }}>
              <span>Stage 1 (5%)</span>
              <span>45% Complete</span>
            </div>
            <div style={{ height: '12px', background: 'rgba(255,255,255,0.1)', borderRadius: '6px', overflow: 'hidden' }}>
              <div style={{ width: '45%', height: '100%', background: 'var(--color-accent-gradient)', transition: 'width 1s ease' }}></div>
            </div>
          </div>
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--color-success)' }}>0.2%</div>
            <div style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>Failure Rate</div>
          </div>
        </div>
      </div>
    </div>
  );
}
