import styles from './Stat.module.css';

/** A labelled reading: the label above, the value in mono below. */
export default function Stat({ label, value, detail, size = 'md' }: {
  label: string;
  value: React.ReactNode;
  detail?: React.ReactNode;
  size?: 'md' | 'lg';
}) {
  return (
    <div className={styles.stat}>
      <div className="eyebrow">{label}</div>
      <div className={`${styles.value} ${styles[size]} mono`}>{value}</div>
      {detail && <div className={styles.detail}>{detail}</div>}
    </div>
  );
}
