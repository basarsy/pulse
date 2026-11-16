import styles from './EmptyState.module.css';

/** One line in the product's voice, with an optional action. */
export default function EmptyState({ children, action, inset = true }: {
  children: React.ReactNode;
  action?: React.ReactNode;
  /** Its own padding, for panels whose content is flush with the edges. */
  inset?: boolean;
}) {
  return (
    <div className={`${styles.empty} ${inset ? styles.inset : ''}`}>
      <p>{children}</p>
      {action}
    </div>
  );
}
