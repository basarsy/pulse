import React, { useState } from 'react';
import { useParams } from 'react-router-dom';
import Button from '../components/Button';
import Badge from '../components/Badge';

export default function VehicleDetailPage() {
  const { id } = useParams();
  const [activeTab, setActiveTab] = useState('overview');

  const tabs = [
    { id: 'overview', label: 'Overview' },
    { id: 'commands', label: 'Commands' },
    { id: 'telemetry', label: 'Telemetry' },
    { id: 'updates', label: 'Updates' }
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '2rem' }}>
        <div>
          <h2>2023 Model S</h2>
          <div style={{ color: 'var(--color-text-secondary)' }}>VIN: 5YJSA1E23PF123456 • SW: v2024.12.3</div>
        </div>
        <Badge color="green">ONLINE</Badge>
      </div>

      <div style={{ display: 'flex', gap: '2rem', borderBottom: '1px solid var(--color-border)', marginBottom: '2rem' }}>
        {tabs.map(tab => (
          <div 
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            style={{ 
              paddingBottom: '1rem', 
              cursor: 'pointer',
              color: activeTab === tab.id ? 'var(--color-accent-primary)' : 'var(--color-text-secondary)',
              borderBottom: activeTab === tab.id ? '2px solid var(--color-accent-primary)' : '2px solid transparent',
              fontWeight: activeTab === tab.id ? 600 : 400
            }}
          >
            {tab.label}
          </div>
        ))}
      </div>

      <div className="tab-content">
        {activeTab === 'overview' && (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '2rem' }}>
            <div className="glass-panel" style={{ padding: '1.5rem', minHeight: '300px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', background: 'rgba(59,130,246,0.05)' }}>
              {/* Map placeholder */}
              <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>🗺️</div>
              <div style={{ fontFamily: 'monospace', color: 'var(--color-text-secondary)' }}>Lat: 37.7749° N, Lng: -122.4194° W</div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div className="glass-panel" style={{ padding: '1.5rem' }}>
                <h4 style={{ margin: '0 0 1rem 0' }}>Status Summary</h4>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                  <div>
                    <div style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary)' }}>Odometer</div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 600 }}>12,450 km</div>
                  </div>
                  <div>
                    <div style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary)' }}>Battery</div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 600 }}>85%</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'commands' && (
          <div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: '1rem', marginBottom: '2rem' }}>
              {['LOCK_DOORS', 'UNLOCK_DOORS', 'ENGINE_START', 'HONK_HORN', 'FLASH_LIGHTS'].map(cmd => (
                <Button key={cmd} variant="secondary" onClick={() => console.log('Command:', cmd)} style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                  <span style={{ fontSize: '1.5rem' }}>⚡</span>
                  <span style={{ fontSize: '0.75rem' }}>{cmd.replace('_', ' ')}</span>
                </Button>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'telemetry' && (
          <div className="glass-panel" style={{ padding: '2rem', textAlign: 'center', color: 'var(--color-text-secondary)' }}>
            Live Telemetry Charts (Coming Soon)
          </div>
        )}
        
        {activeTab === 'updates' && (
          <div className="glass-panel" style={{ padding: '2rem' }}>
            <h4>No Updates Available</h4>
            <p style={{ color: 'var(--color-text-secondary)' }}>Vehicle is on the latest software version.</p>
          </div>
        )}
      </div>
    </div>
  );
}
