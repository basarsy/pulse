import { useEffect, useRef, useState } from 'react';
import { Bell } from 'lucide-react';
import { useNotifications } from '../context/notifications';
import { clock, notificationTone } from '../utils/format';
import Status from './Status';
import styles from './NotificationBell.module.css';

export default function NotificationBell() {
  const { unreadCount, notifications, markAllAsRead } = useNotifications();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);

  // Close on a click outside or on Escape.
  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => { if (!root.current?.contains(e.target as Node)) setOpen(false); };
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div className={styles.root} ref={root}>
      <button
        className={styles.bell}
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        aria-label={unreadCount ? `Notifications, ${unreadCount} unread` : 'Notifications'}
      >
        <Bell size={18} strokeWidth={1.75} aria-hidden />
        {unreadCount > 0 && <span className={`${styles.count} mono`} aria-hidden>{unreadCount}</span>}
      </button>
      {open && (
        <div className={styles.menu} role="dialog" aria-label="Notifications">
          <div className={styles.menuHeader}>
            <span className={styles.menuTitle}>Notifications</span>
            {notifications.length > 0 && (
              <button className={styles.markRead} onClick={markAllAsRead}>Mark all read</button>
            )}
          </div>
          {notifications.length === 0 ? (
            <p className={styles.empty}>Nothing yet. Command results and finished rollouts show up here.</p>
          ) : (
            <ul className={styles.list}>
              {notifications.map(n => (
                <li key={n.id} className={n.read ? styles.read : ''}>
                  <Status tone={notificationTone[n.type]}>{n.message}</Status>
                  <span className={`${styles.time} mono`}>{clock(n.timestamp)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
