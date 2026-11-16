import { X } from 'lucide-react';
import type { Notification } from '../context/notifications';
import { notificationTone } from '../utils/format';
import Status from './Status';
import styles from './Toast.module.css';

export default function Toast({ notification, onClose }: { notification: Notification; onClose: () => void }) {
  return (
    <div className={styles.toast} role="status">
      <Status tone={notificationTone[notification.type]}>{notification.message}</Status>
      <button className={styles.close} onClick={onClose} aria-label="Dismiss notification">
        <X size={16} strokeWidth={1.75} aria-hidden />
      </button>
    </div>
  );
}

/** Where toasts stack: top right, newest first. */
export function ToastStack({ children }: { children: React.ReactNode }) {
  return <div className={styles.stack}>{children}</div>;
}
