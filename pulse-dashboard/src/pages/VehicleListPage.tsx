import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Badge from '../components/Badge';
import Button from '../components/Button';
import { vehicleApi } from '../api/endpoints';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { connectivityColor, num, timeAgo } from '../utils/format';

function RegisterVehicleForm({ onRegistered }: { onRegistered: () => void }) {
  const [vin, setVin] = useState('');
  const [model, setModel] = useState('');
  const [modelYear, setModelYear] = useState(new Date().getFullYear());
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      await vehicleApi.register(vin.trim().toUpperCase(), model.trim(), modelYear);
      setVin('');
      setModel('');
      onRegistered();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not register vehicle');
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="glass-panel" onSubmit={submit} style={{ padding: '1.5rem', marginBottom: '2rem', display: 'grid', gridTemplateColumns: '2fr 2fr 1fr auto', gap: '1rem', alignItems: 'end' }}>
      <div className="form-group" style={{ margin: 0 }}>
        <label className="form-label">VIN (17 characters)</label>
        <input className="form-input" required minLength={17} maxLength={17} value={vin} onChange={e => setVin(e.target.value)} />
      </div>
      <div className="form-group" style={{ margin: 0 }}>
        <label className="form-label">Model</label>
        <input className="form-input" required value={model} onChange={e => setModel(e.target.value)} placeholder="BMW iX xDrive50" />
      </div>
      <div className="form-group" style={{ margin: 0 }}>
        <label className="form-label">Year</label>
        <input className="form-input" type="number" required min={1990} max={2100} value={modelYear} onChange={e => setModelYear(Number(e.target.value))} />
      </div>
      <Button type="submit" isLoading={saving}>Register</Button>
      {error && <div style={{ gridColumn: '1 / -1', color: 'var(--color-danger)' }}>{error}</div>}
    </form>
  );
}

export default function VehicleListPage() {
  const navigate = useNavigate();
  const { isAdmin } = useAuth();
  const [showForm, setShowForm] = useState(false);
  // Connectivity is not pushed over WebSocket, so the list refreshes periodically.
  const { data: vehicles, error, loading, reload } = useAsync(isAdmin ? vehicleApi.fleet : vehicleApi.mine, [isAdmin], 15000);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h2>{isAdmin ? 'Fleet Vehicles' : 'My Vehicles'}</h2>
        <Button variant={showForm ? 'secondary' : 'primary'} onClick={() => setShowForm(!showForm)}>
          {showForm ? 'Close' : '+ Register Vehicle'}
        </Button>
      </div>

      {showForm && <RegisterVehicleForm onRegistered={() => { setShowForm(false); reload(); }} />}
      {error && <div style={{ color: 'var(--color-danger)', marginBottom: '1rem' }}>{error}</div>}
      {loading && !vehicles && <div>Loading vehicles…</div>}
      {vehicles?.length === 0 && (
        <div className="glass-panel" style={{ padding: '2rem', textAlign: 'center', color: 'var(--color-text-secondary)' }}>
          No vehicles yet. Register one to start sending remote commands.
        </div>
      )}

      <div className="grid-cards">
        {vehicles?.map(v => (
          <div
            key={v.id}
            className="glass-panel"
            style={{ padding: '1.5rem', cursor: 'pointer', transition: 'transform 0.2s ease' }}
            onClick={() => navigate(`/vehicles/${v.id}`)}
            onMouseEnter={e => (e.currentTarget.style.transform = 'translateY(-5px)')}
            onMouseLeave={e => (e.currentTarget.style.transform = 'translateY(0)')}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: '1.25rem' }}>{v.modelYear} {v.model}</h3>
                <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem', marginTop: '0.25rem' }}>VIN: {v.vin}</div>
              </div>
              <Badge color={connectivityColor[v.connectivityState]}>{v.connectivityState}</Badge>
            </div>

            <div style={{ marginTop: '1.5rem', display: 'flex', alignItems: 'center', gap: '1rem' }}>
              <div style={{
                width: '50px', height: '50px', borderRadius: '50%',
                background: `conic-gradient(var(--color-accent-primary) ${v.batteryOrFuelLevel ?? 0}%, rgba(255,255,255,0.1) 0)`,
                display: 'flex', alignItems: 'center', justifyContent: 'center'
              }}>
                <div style={{
                  width: '40px', height: '40px', borderRadius: '50%', backgroundColor: 'var(--color-bg-secondary)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.75rem', fontWeight: 'bold'
                }}>
                  {num(v.batteryOrFuelLevel, 0, '%')}
                </div>
              </div>
              <div style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary)' }}>
                <div>Software {v.currentSoftwareVersion ?? 'unknown'}</div>
                <div>Last seen {timeAgo(v.lastSeenAt)}</div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
