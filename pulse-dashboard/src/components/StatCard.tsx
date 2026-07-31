import React from 'react';

interface StatCardProps {
  title: string;
  value: string | number;
  icon: string;
  trend?: string;
}

export default function StatCard({ title, value, icon, trend }: StatCardProps) {
  return (
    <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
      <div style={{
        width: '48px', height: '48px', borderRadius: 'var(--radius-md)',
        backgroundColor: 'rgba(59, 130, 246, 0.1)', color: 'var(--color-accent-primary)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.5rem'
      }}>
        {icon}
      </div>
      <div>
        <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem', marginBottom: '0.25rem' }}>{title}</div>
        <div style={{ fontSize: '1.75rem', fontWeight: 700 }}>{value}</div>
        {trend && <div style={{ fontSize: '0.75rem', color: 'var(--color-success)', marginTop: '0.25rem' }}>{trend}</div>}
      </div>
    </div>
  );
}
