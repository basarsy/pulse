import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import Badge from '../components/Badge';
import Button from '../components/Button';
import { otaApi, vehicleApi } from '../api/endpoints';
import type { Campaign, SoftwareVersion } from '../api/types';
import { useTopic } from '../context/LiveContext';
import { useAsync } from '../hooks/useAsync';
import { campaignColor, label, upsertById } from '../utils/format';

export function campaignProgress(c: Campaign): number {
  if (!c.totalVehiclesTargeted) return c.status === 'COMPLETED' ? 100 : 0;
  return Math.round(((c.vehiclesInstalled + c.vehiclesFailed) / c.totalVehiclesTargeted) * 100);
}

function NewVersionForm({ onCreated }: { onCreated: (v: SoftwareVersion) => void }) {
  const [form, setForm] = useState({ versionLabel: '', packageUrl: '', checksum: '', releaseNotes: '' });
  const [error, setError] = useState('');

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      onCreated(await otaApi.createVersion(form.versionLabel, form.packageUrl, form.checksum, form.releaseNotes));
      setForm({ versionLabel: '', packageUrl: '', checksum: '', releaseNotes: '' });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create version');
    }
  };

  const field = (key: keyof typeof form, title: string, placeholder = '') => (
    <div className="form-group" style={{ margin: 0 }}>
      <label className="form-label">{title}</label>
      <input className="form-input" required={key !== 'releaseNotes'} placeholder={placeholder}
             value={form[key]} onChange={e => setForm({ ...form, [key]: e.target.value })} />
    </div>
  );

  return (
    <form onSubmit={submit} style={{ display: 'grid', gridTemplateColumns: '1fr 2fr 1fr 2fr auto', gap: '1rem', alignItems: 'end' }}>
      {field('versionLabel', 'Version', '2026.11.1')}
      {field('packageUrl', 'Package URL', 'https://ota.example.com/pkg.bin')}
      {field('checksum', 'Checksum', 'sha256:…')}
      {field('releaseNotes', 'Release notes')}
      <Button type="submit" variant="secondary">Add version</Button>
      {error && <div style={{ gridColumn: '1 / -1', color: 'var(--color-danger)' }}>{error}</div>}
    </form>
  );
}

function NewRolloutForm({ onCreated }: { onCreated: (c: Campaign) => void }) {
  const versions = useAsync(otaApi.versions, []);
  const fleet = useAsync(vehicleApi.fleet, []);
  const models = [...new Set((fleet.data ?? []).map(v => v.model))].sort();
  const [versionId, setVersionId] = useState('');
  const [targetModel, setTargetModel] = useState('');
  const [stages, setStages] = useState('5, 25, 100');
  const [threshold, setThreshold] = useState(10);
  const [error, setError] = useState('');

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const percentages = stages.split(',').map(s => Number(s.trim())).filter(n => n > 0 && n <= 100);
    if (!percentages.length) {
      setError('Enter stage percentages, e.g. 5, 25, 100');
      return;
    }
    try {
      onCreated(await otaApi.createCampaign(versionId, targetModel, percentages, threshold));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create rollout');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      <NewVersionForm onCreated={v => { versions.setData(list => [v, ...(list ?? [])]); setVersionId(v.id); }} />
      <form onSubmit={submit} style={{ display: 'grid', gridTemplateColumns: '2fr 2fr 1fr 1fr auto', gap: '1rem', alignItems: 'end' }}>
        <div className="form-group" style={{ margin: 0 }}>
          <label className="form-label">Software version</label>
          <select className="form-input" required value={versionId} onChange={e => setVersionId(e.target.value)}>
            <option value="">Select…</option>
            {versions.data?.map(v => <option key={v.id} value={v.id}>{v.versionLabel}</option>)}
          </select>
        </div>
        <div className="form-group" style={{ margin: 0 }}>
          <label className="form-label">Target model</label>
          <input className="form-input" required list="fleet-models" value={targetModel} onChange={e => setTargetModel(e.target.value)} />
          <datalist id="fleet-models">{models.map(m => <option key={m} value={m} />)}</datalist>
        </div>
        <div className="form-group" style={{ margin: 0 }}>
          <label className="form-label">Stages (%)</label>
          <input className="form-input" value={stages} onChange={e => setStages(e.target.value)} />
        </div>
        <div className="form-group" style={{ margin: 0 }}>
          <label className="form-label">Pause above (% failed)</label>
          <input className="form-input" type="number" min={0} max={100} value={threshold} onChange={e => setThreshold(Number(e.target.value))} />
        </div>
        <Button type="submit">Create rollout</Button>
        {error && <div style={{ gridColumn: '1 / -1', color: 'var(--color-danger)' }}>{error}</div>}
      </form>
    </div>
  );
}

export default function RolloutListPage() {
  const [showForm, setShowForm] = useState(false);
  const campaigns = useAsync(otaApi.campaigns, []);
  useTopic<Campaign>('/topic/rollouts/*', campaign => campaigns.setData(list => upsertById(list, campaign)));

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h2>OTA Campaigns</h2>
        <Button variant={showForm ? 'secondary' : 'primary'} onClick={() => setShowForm(!showForm)}>
          {showForm ? 'Close' : '+ New Rollout'}
        </Button>
      </div>

      {showForm && (
        <div className="glass-panel" style={{ padding: '1.5rem', marginBottom: '2rem' }}>
          <NewRolloutForm onCreated={c => { campaigns.setData(list => upsertById(list, c)); setShowForm(false); }} />
        </div>
      )}
      {campaigns.error && <div style={{ color: 'var(--color-danger)' }}>{campaigns.error}</div>}

      <div className="glass-panel" style={{ overflow: 'hidden' }}>
        <table>
          <thead>
            <tr><th>Version</th><th>Target model</th><th>Status</th><th>Stage</th><th>Progress</th><th /></tr>
          </thead>
          <tbody>
            {campaigns.data?.length === 0 && (
              <tr><td colSpan={6} style={{ color: 'var(--color-text-secondary)' }}>No rollouts yet.</td></tr>
            )}
            {campaigns.data?.map(c => {
              const progress = campaignProgress(c);
              return (
                <tr key={c.id}>
                  <td style={{ fontWeight: 600 }}>{c.softwareVersion.versionLabel}</td>
                  <td>{c.targetModel}</td>
                  <td><Badge color={campaignColor[c.status]}>{label(c.status)}</Badge></td>
                  <td>{Math.min(c.currentStageIndex + 1, c.stages.length)} / {c.stages.length}</td>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                      <div style={{ flex: 1, height: '8px', background: 'rgba(255,255,255,0.1)', borderRadius: '4px', overflow: 'hidden' }}>
                        <div style={{ width: `${progress}%`, height: '100%', background: c.status === 'COMPLETED' ? 'var(--color-success)' : 'var(--color-accent-primary)' }} />
                      </div>
                      <span style={{ fontSize: '0.875rem' }}>{c.vehiclesInstalled}/{c.totalVehiclesTargeted}</span>
                    </div>
                  </td>
                  <td><Link to={`/rollouts/${c.id}`} style={{ fontSize: '0.875rem' }}>Details</Link></td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
