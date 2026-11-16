import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, X } from 'lucide-react';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import ProgressBar from '../components/ProgressBar';
import Status from '../components/Status';
import { otaApi, vehicleApi } from '../api/endpoints';
import type { Campaign, SoftwareVersion } from '../api/types';
import { useTopic } from '../context/live';
import { useAsync } from '../hooks/useAsync';
import { campaignProgress, campaignTone, label, timeAgo, upsertById } from '../utils/format';
import styles from './RolloutListPage.module.css';

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
      setError(err instanceof Error ? err.message : 'Could not add the version');
    }
  };

  const field = (key: keyof typeof form, title: string, placeholder = '', mono = false) => (
    <div className="field">
      <label htmlFor={`version-${key}`}>{title}</label>
      <input id={`version-${key}`} className={`input ${mono ? 'mono' : ''}`} required={key !== 'releaseNotes'} placeholder={placeholder}
             value={form[key]} onChange={e => setForm({ ...form, [key]: e.target.value })} />
    </div>
  );

  return (
    <form onSubmit={submit} className={styles.versionForm} aria-labelledby="new-version">
      <h3 id="new-version" className="eyebrow">1 · Software version</h3>
      <div className={styles.versionFields}>
        {field('versionLabel', 'Version', '2026.11.1', true)}
        {field('packageUrl', 'Package URL', 'https://ota.example.com/pkg.bin', true)}
        {field('checksum', 'Checksum', 'sha256:…', true)}
        {field('releaseNotes', 'Release notes (optional)')}
        <Button type="submit" variant="secondary">Add version</Button>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
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
    // Only text that isn't a number is caught here; the server checks range, order and the final 100%.
    const parts = stages.split(',').map(s => s.trim());
    if (!parts.every(part => /^\d+$/.test(part))) {
      setError('Stages must be whole numbers, e.g. 5, 25, 100');
      return;
    }
    const percentages = parts.map(Number);
    try {
      onCreated(await otaApi.createCampaign(versionId, targetModel, percentages, threshold));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the rollout');
    }
  };

  return (
    <div className={styles.forms}>
      <p className="muted">Pick an existing version, or add a new one first. A rollout reaches a growing share of one model&apos;s fleet,
        stage by stage, and pauses itself if too many installs in a stage fail.</p>
      <NewVersionForm onCreated={v => { versions.setData(list => [v, ...(list ?? [])]); setVersionId(v.id); }} />
      <form onSubmit={submit} className={styles.versionForm} aria-labelledby="new-rollout">
        <h3 id="new-rollout" className="eyebrow">2 · Rollout</h3>
        <div className={styles.rolloutFields}>
          <div className="field">
            <label htmlFor="rollout-version">Software version</label>
            <select id="rollout-version" className="input mono" required value={versionId} onChange={e => setVersionId(e.target.value)}>
              <option value="">Select…</option>
              {versions.data?.map(v => <option key={v.id} value={v.id}>{v.versionLabel}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="rollout-model">Target model</label>
            <input id="rollout-model" className="input" required list="fleet-models" value={targetModel} onChange={e => setTargetModel(e.target.value)} />
            <datalist id="fleet-models">{models.map(m => <option key={m} value={m} />)}</datalist>
          </div>
          <div className="field">
            <label htmlFor="rollout-stages">Stages (% of the model)</label>
            <input id="rollout-stages" className="input mono" value={stages} onChange={e => setStages(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="rollout-threshold">Pause above (% failed)</label>
            <input id="rollout-threshold" className="input mono" type="number" min={0} max={100} value={threshold}
                   onChange={e => setThreshold(Number(e.target.value))} />
          </div>
          <Button type="submit">Create rollout</Button>
        </div>
        {error && <p className="error" role="alert">{error}</p>}
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
      <PageHeader
        title="OTA updates"
        meta="Software rollouts, stage by stage."
        actions={
          <Button variant={showForm ? 'secondary' : 'primary'} icon={showForm ? X : Plus} onClick={() => setShowForm(!showForm)}>
            {showForm ? 'Close' : 'New rollout'}
          </Button>
        }
      />
      {showForm && (
        <Panel title="New rollout" className={styles.formPanel}>
          <NewRolloutForm onCreated={c => { campaigns.setData(list => upsertById(list, c)); setShowForm(false); }} />
        </Panel>
      )}
      {campaigns.error && <p className="error" role="alert">{campaigns.error}</p>}

      <Panel flush>
        {campaigns.data?.length === 0 ? (
          <EmptyState>No rollouts yet. Create one to send a software version to part of the fleet.</EmptyState>
        ) : (
          <table className="table">
            <thead>
              <tr><th>Version</th><th>Target model</th><th>Status</th><th>Stage</th><th>Progress</th><th>Created</th></tr>
            </thead>
            <tbody>
              {campaigns.data?.map(c => (
                <tr key={c.id}>
                  <td><Link to={`/rollouts/${c.id}`} className="row-link mono">{c.softwareVersion.versionLabel}</Link></td>
                  <td>{c.targetModel}</td>
                  <td><Status tone={campaignTone[c.status]}>{label(c.status)}</Status></td>
                  <td className="mono">{Math.min(c.currentStageIndex + 1, c.stages.length)} of {c.stages.length}</td>
                  <td>
                    <div className={styles.progress}>
                      <ProgressBar value={campaignProgress(c)} size="sm" label={`Rollout ${c.softwareVersion.versionLabel} progress`}
                                   tone={c.status === 'COMPLETED' ? 'ok' : 'accent'} />
                      <span className="mono muted">{c.vehiclesInstalled}/{c.totalVehiclesTargeted}</span>
                    </div>
                  </td>
                  <td className="muted">{timeAgo(c.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}
