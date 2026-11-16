import styles from './Wordmark.module.css';

/** The Pulse mark: a heartbeat trace in the accent colour, and the name. */
export default function Wordmark({ size = 'md' }: { size?: 'md' | 'lg' }) {
  return (
    <span className={`${styles.wordmark} ${styles[size]}`}>
      <svg viewBox="0 0 32 32" aria-hidden className={styles.mark}>
        <path d="M3 17h7l3-8 5 15 3-7h8" fill="none" stroke="currentColor" strokeWidth="2.5"
              strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      Pulse
    </span>
  );
}
