import React, { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Plus, X } from 'lucide-react';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import ProgressBar from '../components/ProgressBar';
import Status from '../components/Status';
import { vehicleApi } from '../api/endpoints';
import { useAuth } from '../context/auth';
import { useAsync } from '../hooks/useAsync';
import { connectivityTone, label, num, timeAgo } from '../utils/format';
import styles from './VehicleListPage.module.css';

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
      setError(err instanceof Error ? err.message : 'Could not register the vehicle');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Panel title="Register a vehicle" className={styles.formPanel}>
      <form onSubmit={submit} className={styles.form}>
        <div className="field">
          <label htmlFor="vin">VIN</label>
          <input id="vin" className="input mono" required minLength={17} maxLength={17} value={vin}
                 onChange={e => setVin(e.target.value)} placeholder="17 characters" />
        </div>
        <div className="field">
          <label htmlFor="model">Model</label>
          <input id="model" className="input" required value={model} onChange={e => setModel(e.target.value)} placeholder="e.g. Pulse Aurora" />
        </div>
        <div className="field">
          <label htmlFor="year">Year</label>
          <input id="year" className="input mono" type="number" required min={1990} max={2100} value={modelYear}
                 onChange={e => setModelYear(Number(e.target.value))} />
        </div>
        <Button type="submit" isLoading={saving}>Register</Button>
        {error && <p className={`error ${styles.formError}`} role="alert">{error}</p>}
      </form>
    </Panel>
  );
}

export default function VehicleListPage() {
  const { isAdmin } = useAuth();
  // ?register=1 (the overview's "Register a vehicle" shortcut) opens the form straight away.
  const [searchParams] = useSearchParams();
  const [showForm, setShowForm] = useState(searchParams.get('register') === '1');
  // Connectivity is not pushed over WebSocket, so the list refreshes periodically.
  const { data: vehicles, error, loading, reload } = useAsync(isAdmin ? vehicleApi.fleet : vehicleApi.mine, [isAdmin], 15000);
  const online = vehicles?.filter(v => v.connectivityState === 'ONLINE').length ?? 0;

  return (
    <div>
      <PageHeader
        title={isAdmin ? 'Fleet' : 'My vehicles'}
        meta={vehicles && vehicles.length > 0 && <span className="mono">{vehicles.length} vehicles · {online} online</span>}
        actions={
          <Button variant={showForm ? 'secondary' : 'primary'} icon={showForm ? X : Plus} onClick={() => setShowForm(!showForm)}>
            {showForm ? 'Close' : 'Register vehicle'}
          </Button>
        }
      />
      {showForm && <RegisterVehicleForm onRegistered={() => { setShowForm(false); reload(); }} />}
      {error && <p className="error" role="alert">{error}</p>}

      <Panel flush>
        {loading && !vehicles && <EmptyState>Loading vehicles…</EmptyState>}
        {vehicles?.length === 0 && (
          <EmptyState action={!showForm && <Button variant="secondary" icon={Plus} onClick={() => setShowForm(true)}>Register vehicle</Button>}>
            No vehicles yet. Register one to start sending remote commands.
          </EmptyState>
        )}
        {vehicles && vehicles.length > 0 && (
          <table className="table">
            <thead>
              <tr>
                <th>Vehicle</th>
                <th className={styles.vin}>VIN</th>
                <th>Status</th>
                <th>Battery</th>
                <th>Software</th>
                <th>Last seen</th>
                {isAdmin && <th>Owner</th>}
              </tr>
            </thead>
            <tbody>
              {vehicles.map(v => (
                <tr key={v.id}>
                  <td>
                    <Link to={`/vehicles/${v.id}`} className="row-link">{v.model}</Link>
                    <span className="muted mono"> {v.modelYear}</span>
                  </td>
                  <td className={`mono muted ${styles.vin}`}>{v.vin}</td>
                  <td><Status tone={connectivityTone[v.connectivityState]}>{label(v.connectivityState)}</Status></td>
                  <td>
                    {v.batteryOrFuelLevel === null ? <span className="muted">—</span> : (
                      <div className={styles.battery}>
                        <ProgressBar value={v.batteryOrFuelLevel} size="sm" label={`Battery of ${v.vin}`}
                                     tone={v.batteryOrFuelLevel < 20 ? 'bad' : 'ok'} />
                        <span className="mono">{num(v.batteryOrFuelLevel, 0, '%')}</span>
                      </div>
                    )}
                  </td>
                  <td className="mono">{v.currentSoftwareVersion ?? '—'}</td>
                  <td className="muted">{timeAgo(v.lastSeenAt)}</td>
                  {isAdmin && <td className="muted">{v.ownerEmail}</td>}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}
