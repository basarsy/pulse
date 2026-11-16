import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import styles from './BackLink.module.css';

/** "← Vehicles": back to the list a detail page belongs to. */
export default function BackLink({ to, children }: { to: string; children: React.ReactNode }) {
  return <Link to={to} className={styles.back}><ArrowLeft size={14} aria-hidden /> {children}</Link>;
}
