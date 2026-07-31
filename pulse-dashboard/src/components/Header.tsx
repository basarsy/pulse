import React from 'react';
import NotificationBell from './NotificationBell';
import { useAuth } from '../context/AuthContext';
import Button from './Button';

export default function Header() {
  const { logout } = useAuth();

  return (
    <header style={{
      height: '70px',
      borderBottom: '1px solid var(--color-border)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '0 2rem',
      backgroundColor: 'var(--color-bg-primary)'
    }}>
      <div>
        <input type="text" placeholder="Search..." className="form-input" style={{ width: '300px', padding: '0.5rem 1rem', borderRadius: 'var(--radius-full)' }} />
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
        <NotificationBell />
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div style={{ width: '36px', height: '36px', borderRadius: '50%', backgroundColor: 'var(--color-accent-primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold' }}>
            A
          </div>
          <Button variant="ghost" size="sm" onClick={logout}>Logout</Button>
        </div>
      </div>
    </header>
  );
}
