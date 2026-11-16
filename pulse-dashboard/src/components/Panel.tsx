import styles from './Panel.module.css';

interface PanelProps {
  title?: React.ReactNode;
  /** Right-hand side of the header row: a count, a link or an action. */
  aside?: React.ReactNode;
  /** Content flush with the edges, e.g. a table. */
  flush?: boolean;
  className?: string;
  children: React.ReactNode;
}

/** A flat surface with an optional header row. */
export default function Panel({ title, aside, flush = false, className = '', children }: PanelProps) {
  return (
    <section className={`${styles.panel} ${className}`}>
      {(title || aside) && (
        <header className={styles.header}>
          {title && <h2 className={styles.title}>{title}</h2>}
          {aside && <div className={styles.aside}>{aside}</div>}
        </header>
      )}
      <div className={flush ? styles.flush : styles.body}>{children}</div>
    </section>
  );
}
