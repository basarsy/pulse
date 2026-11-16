import styles from './ProgressBar.module.css';

/**
 * A flat progress bar. {@code value} is the main fill; {@code failed}, if given, is a second
 * segment in the failure colour right after it (e.g. installed, then failed). {@code label}
 * names it for screen readers; the value it reports is the total of both segments.
 */
export default function ProgressBar({ value, failed = 0, label, tone = 'accent', size = 'md' }: {
  value: number;
  failed?: number;
  label: string;
  tone?: 'accent' | 'ok' | 'bad';
  size?: 'sm' | 'md';
}) {
  const clamp = (n: number) => Math.max(0, Math.min(100, Math.round(n)));
  const main = clamp(value);
  const bad = Math.min(clamp(failed), 100 - main);
  return (
    <div className={`${styles.track} ${styles[size]}`} role="progressbar" aria-label={label}
         aria-valuenow={main + bad} aria-valuemin={0} aria-valuemax={100}>
      <div className={`${styles.fill} ${styles[tone]}`} style={{ width: `${main}%` }} />
      {bad > 0 && <div className={`${styles.fill} ${styles.bad}`} style={{ width: `${bad}%` }} />}
    </div>
  );
}
