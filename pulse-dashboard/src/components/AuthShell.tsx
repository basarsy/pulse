import Wordmark from './Wordmark';
import styles from './AuthShell.module.css';

/** The frame around sign-in and registration: the mark, one line about Pulse, and the form. */
export default function AuthShell({ title, children, footer }: {
  title: string;
  children: React.ReactNode;
  footer: React.ReactNode;
}) {
  return (
    <div className={styles.page}>
      <main className={styles.column}>
        <div className={styles.intro}>
          <Wordmark size="lg" />
          <p>Remote commands and over-the-air updates for a connected fleet.</p>
        </div>
        <section className={styles.card} aria-labelledby="auth-title">
          <h1 id="auth-title" className={styles.title}>{title}</h1>
          {children}
        </section>
        <p className={styles.footer}>{footer}</p>
      </main>
    </div>
  );
}
