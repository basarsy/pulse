import { LogOut } from 'lucide-react';
import NotificationBell from './NotificationBell';
import Button from './Button';
import Status from './Status';
import { useAuth } from '../context/auth';
import { useLive } from '../context/live';
import { label } from '../utils/format';
import styles from './Header.module.css';

export default function Header() {
  const { user, logout } = useAuth();
  const { connected } = useLive();
  const initials = (user?.fullName ?? '?').split(' ').map(part => part[0]).join('').slice(0, 2).toUpperCase();

  return (
    <header className={styles.header}>
      <div>
        {connected
          ? <Status tone="ok" pulse>Live</Status>
          : <Status tone="warn">Reconnecting…</Status>}
        {/* Announced only when the connection changes. */}
        <span className="visually-hidden" role="status">
          {connected ? 'Live updates connected' : 'Live updates disconnected, reconnecting'}
        </span>
      </div>
      <div className={styles.right}>
        <NotificationBell />
        <div className={styles.user}>
          <span className={styles.avatar} aria-hidden>{initials}</span>
          <div className={styles.who}>
            <span className={styles.name}>{user?.fullName}</span>
            <span className={styles.role}>{user?.roles.map(label).join(', ')}</span>
          </div>
        </div>
        <Button variant="ghost" size="sm" icon={LogOut} onClick={logout}>Sign out</Button>
      </div>
    </header>
  );
}
