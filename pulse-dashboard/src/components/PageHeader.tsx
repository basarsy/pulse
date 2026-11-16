import styles from './PageHeader.module.css';

/** Page title with an optional line of context underneath and actions on the right. */
export default function PageHeader({ title, meta, actions, eyebrow }: {
  title: React.ReactNode;
  meta?: React.ReactNode;
  actions?: React.ReactNode;
  eyebrow?: React.ReactNode;
}) {
  return (
    <div className={styles.header}>
      <div className={styles.text}>
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h1 className={styles.title}>{title}</h1>
        {meta && <div className={styles.meta}>{meta}</div>}
      </div>
      {actions && <div className={styles.actions}>{actions}</div>}
    </div>
  );
}
