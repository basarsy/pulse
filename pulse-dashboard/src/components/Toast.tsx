import React from 'react';
import { Notification } from '../context/NotificationContext';

export default function Toast({ notification, onClose }: { notification: Notification, onClose: () => void }) {
  return (
    <div className="glass-panel" style={{
      padding: '1rem',
      minWidth: '300px',
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      animation: 'slide-in-right 0.3s ease-out',
      borderLeft: `4px solid ${
        notification.type === 'success' ? 'var(--color-success)' :
        notification.type === 'error' ? 'var(--color-danger)' :
        notification.type === 'warning' ? 'var(--color-warning)' : 'var(--color-accent-primary)'
      }`
    }}>
      <div>
        <div style={{ fontSize: '0.875rem' }}>{notification.message}</div>
      </div>
      <button onClick={onClose} style={{
        background: 'none', border: 'none', color: 'var(--color-text-secondary)', cursor: 'pointer', fontSize: '1.2rem'
      }}>×</button>
    </div>
  );
}
