import styles from './Stat.module.css';

/** A labelled reading. {@code mono} is for numbers, versions and VINs, so they line up. */
export default function Stat({ label, value, detail, size = 'md', mono = false }: {
  label: string;
  value: React.ReactNode;
  detail?: React.ReactNode;
  size?: 'md' | 'lg';
  mono?: boolean;
}) {
  return (
    <div className={styles.stat}>
      <div className="eyebrow">{label}</div>
      <div className={`${styles.value} ${styles[size]} ${mono ? 'mono' : ''}`}>{value}</div>
      {detail && <div className={styles.detail}>{detail}</div>}
    </div>
  );
}
