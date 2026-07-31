import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import Badge from '../components/Badge';

export default function VehicleListPage() {
  const navigate = useNavigate();
  // Dummy data for presentation without backend
  const [vehicles] = useState([
    { id: '1', model: 'Model S', modelYear: 2023, vin: '5YJSA1E23PF...', connectivityState: 'ONLINE', batteryOrFuelLevel: 85, lastSeenAt: new Date().toISOString() },
    { id: '2', model: 'Model 3', modelYear: 2022, vin: '5YJ3E1EA5NF...', connectivityState: 'OFFLINE', batteryOrFuelLevel: 42, lastSeenAt: new Date(Date.now() - 3600000).toISOString() },
    { id: '3', model: 'Model X', modelYear: 2024, vin: '5YJXCEE46RF...', connectivityState: 'SLEEPING', batteryOrFuelLevel: 98, lastSeenAt: new Date(Date.now() - 300000).toISOString() },
  ]);

  const getStateColor = (state: string) => {
    switch (state) {
      case 'ONLINE': return 'green';
      case 'OFFLINE': return 'red';
      case 'SLEEPING': return 'yellow';
      default: return 'gray';
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h2>My Vehicles</h2>
      </div>

      <div className="grid-cards">
        {vehicles.map(v => (
          <div 
            key={v.id} 
            className="glass-panel" 
            style={{ padding: '1.5rem', cursor: 'pointer', transition: 'transform 0.2s ease', position: 'relative' }}
            onClick={() => navigate(`/vehicles/${v.id}`)}
            onMouseEnter={(e) => e.currentTarget.style.transform = 'translateY(-5px)'}
            onMouseLeave={(e) => e.currentTarget.style.transform = 'translateY(0)'}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: '1.25rem' }}>{v.modelYear} {v.model}</h3>
                <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem', marginTop: '0.25rem' }}>VIN: {v.vin}</div>
              </div>
              <Badge color={getStateColor(v.connectivityState)}>
                <span style={{ 
                  display: 'inline-block', width: '8px', height: '8px', borderRadius: '50%', 
                  backgroundColor: 'currentColor', marginRight: '6px',
                  animation: v.connectivityState === 'ONLINE' ? 'pulse-glow 2s infinite' : 'none'
                }}></span>
                {v.connectivityState}
              </Badge>
            </div>

            <div style={{ marginTop: '2rem', display: 'flex', alignItems: 'center', gap: '1rem' }}>
              {/* Fake circular progress */}
              <div style={{
                width: '50px', height: '50px', borderRadius: '50%',
                background: `conic-gradient(var(--color-accent-primary) ${v.batteryOrFuelLevel}%, rgba(255,255,255,0.1) 0)`,
                display: 'flex', alignItems: 'center', justifyContent: 'center'
              }}>
                <div style={{
                  width: '40px', height: '40px', borderRadius: '50%',
                  backgroundColor: 'var(--color-bg-secondary)', display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: '0.75rem', fontWeight: 'bold'
                }}>
                  {v.batteryOrFuelLevel}%
                </div>
              </div>
              <div style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary)' }}>
                Battery Level
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
