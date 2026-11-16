import { useEffect, useRef, useState } from 'react';
import { Bell } from 'lucide-react';
import { useNotifications } from '../context/notifications';
import { clock, notificationTone } from '../utils/format';
import Status from './Status';
import styles from './NotificationBell.module.css';

const MENU_ID = 'notifications-menu';

/** The bell and its notification list: a disclosure that takes focus when opened. */
export default function NotificationBell() {
  const { unreadCount, notifications, markAllAsRead } = useNotifications();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const bell = useRef<HTMLButtonElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);

  const close = (returnFocus: boolean) => {
    setOpen(false);
    if (returnFocus) bell.current?.focus();
  };

  useEffect(() => {
    if (!open) return;
    heading.current?.focus();
    const onClick = (e: MouseEvent) => { if (!root.current?.contains(e.target as Node)) setOpen(false); };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        bell.current?.focus();
      }
    };
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div
      className={styles.root}
      ref={root}
      // Close when focus moves out of the bell and its menu, e.g. by tabbing away.
      onBlur={e => { if (open && !root.current?.contains(e.relatedTarget as Node)) close(false); }}
    >
      <button
        ref={bell}
        className={styles.bell}
        onClick={() => (open ? close(false) : setOpen(true))}
        aria-expanded={open}
        aria-controls={MENU_ID}
        aria-label={unreadCount ? `Notifications, ${unreadCount} unread` : 'Notifications'}
      >
        <Bell size={18} strokeWidth={1.75} aria-hidden />
        {unreadCount > 0 && <span className={`${styles.count} mono`} aria-hidden>{unreadCount}</span>}
      </button>
      {open && (
        <div className={styles.menu} id={MENU_ID}>
          <div className={styles.menuHeader}>
            <h2 className={styles.menuTitle} ref={heading} tabIndex={-1}>Notifications</h2>
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
