import type { Tone } from '../utils/tone';
import styles from './Status.module.css';

/** A status dot with its label. The label is always shown, so colour is never the only signal. */
export default function Status({ tone, children, pulse = false }: { tone: Tone; children: React.ReactNode; pulse?: boolean }) {
  return (
    <span className={`${styles.status} ${styles[tone]}`}>
      <span className={`${styles.dot} ${pulse ? styles.pulse : ''}`} aria-hidden />
      {children}
    </span>
  );
}
