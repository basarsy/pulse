import React, { useState } from 'react';
import { useNotifications } from '../context/NotificationContext';

export default function NotificationBell() {
  const { unreadCount, notifications, markAllAsRead } = useNotifications();
  const [open, setOpen] = useState(false);

  return (
    <div style={{ position: 'relative' }}>
      <button 
        onClick={() => setOpen(!open)}
        style={{
          background: 'none', border: 'none', fontSize: '1.5rem', cursor: 'pointer', position: 'relative',
          color: 'var(--color-text-secondary)'
        }}
      >
        🔔
        {unreadCount > 0 && (
          <span style={{
            position: 'absolute', top: '-5px', right: '-5px',
            background: 'var(--color-danger)', color: 'white',
            borderRadius: '50%', padding: '0.1rem 0.4rem',
            fontSize: '0.7rem', fontWeight: 'bold',
            animation: 'pulse-glow 2s infinite'
          }}>
            {unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="glass-panel" style={{
          position: 'absolute', top: '100%', right: 0, width: '300px', marginTop: '0.5rem',
          maxHeight: '400px', overflowY: 'auto', zIndex: 50
        }}>
          <div style={{ padding: '1rem', borderBottom: '1px solid var(--color-border)', display: 'flex', justifyContent: 'space-between' }}>
            <h4 style={{ margin: 0 }}>Notifications</h4>
            <button onClick={markAllAsRead} style={{ background: 'none', border: 'none', color: 'var(--color-accent-primary)', cursor: 'pointer', fontSize: '0.8rem' }}>Mark all read</button>
          </div>
          <div style={{ padding: '0.5rem' }}>
            {notifications.length === 0 ? (
              <div style={{ padding: '1rem', textAlign: 'center', color: 'var(--color-text-secondary)' }}>No notifications</div>
            ) : (
              notifications.map(n => (
                <div key={n.id} style={{
                  padding: '0.75rem', borderBottom: '1px solid rgba(255,255,255,0.05)',
                  opacity: n.read ? 0.6 : 1
                }}>
                  <div style={{ fontSize: '0.875rem' }}>{n.message}</div>
                  <div style={{ fontSize: '0.7rem', color: 'var(--color-text-secondary)', marginTop: '0.25rem' }}>
                    {n.timestamp.toLocaleTimeString()}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
