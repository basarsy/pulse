import styles from './ProgressBar.module.css';

/** A flat progress bar. {@code label} names it for screen readers. */
export default function ProgressBar({ value, label, tone = 'accent', size = 'md' }: {
  value: number;
  label: string;
  tone?: 'accent' | 'ok' | 'bad';
  size?: 'sm' | 'md';
}) {
  const clamped = Math.max(0, Math.min(100, Math.round(value)));
  return (
    <div className={`${styles.track} ${styles[size]}`} role="progressbar" aria-label={label}
         aria-valuenow={clamped} aria-valuemin={0} aria-valuemax={100}>
      <div className={`${styles.fill} ${styles[tone]}`} style={{ width: `${clamped}%` }} />
    </div>
  );
}
