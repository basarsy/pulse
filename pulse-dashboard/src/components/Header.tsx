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
      <div role="status" title={connected ? 'Receiving live updates' : 'Reconnecting to live updates'}>
        {connected
          ? <Status tone="ok" pulse>Live</Status>
          : <Status tone="warn">Reconnecting…</Status>}
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
