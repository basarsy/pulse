import NotificationBell from './NotificationBell';
import Button from './Button';
import { useAuth } from '../context/AuthContext';
import { useLive } from '../context/LiveContext';
import { label } from '../utils/format';

export default function Header() {
  const { user, logout } = useAuth();
  const { connected } = useLive();
  const initials = (user?.fullName ?? '?').split(' ').map(part => part[0]).join('').slice(0, 2).toUpperCase();

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
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem', color: 'var(--color-text-secondary)' }}>
        <span style={{
          width: '8px', height: '8px', borderRadius: '50%',
          backgroundColor: connected ? 'var(--color-success)' : 'var(--color-warning)'
        }} />
        {connected ? 'Live updates connected' : 'Connecting to live updates…'}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
        <NotificationBell />
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: '0.875rem', fontWeight: 600 }}>{user?.fullName}</div>
            <div style={{ fontSize: '0.75rem', color: 'var(--color-text-secondary)' }}>
              {user?.roles.map(label).join(', ')}
            </div>
          </div>
          <div style={{ width: '36px', height: '36px', borderRadius: '50%', backgroundColor: 'var(--color-accent-primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold' }}>
            {initials}
          </div>
          <Button variant="ghost" size="sm" onClick={logout}>Logout</Button>
        </div>
      </div>
    </header>
  );
}
