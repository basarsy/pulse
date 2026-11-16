import { NavLink } from 'react-router-dom';
import { CloudDownload, Gauge, CarFront, type LucideIcon } from 'lucide-react';
import { useAuth } from '../context/auth';
import Wordmark from './Wordmark';
import styles from './Sidebar.module.css';

interface NavItem {
  path: string;
  label: string;
  icon: LucideIcon;
}

export default function Sidebar() {
  const { isAdmin } = useAuth();
  const links: NavItem[] = [
    { path: '/', label: 'Overview', icon: Gauge },
    { path: '/vehicles', label: 'Vehicles', icon: CarFront },
    ...(isAdmin ? [{ path: '/rollouts', label: 'OTA updates', icon: CloudDownload }] : []),
  ];

  return (
    <aside className={styles.sidebar}>
      <div className={styles.brand}>
        <Wordmark />
        <span className={styles.product}>Fleet console</span>
      </div>
      <nav aria-label="Main">
        <ul className={styles.nav}>
          {links.map(({ path, label, icon: Icon }) => (
            <li key={path}>
              <NavLink to={path} end={path === '/'} className={({ isActive }) => `${styles.link} ${isActive ? styles.active : ''}`}>
                <Icon size={18} strokeWidth={1.75} aria-hidden />
                {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </aside>
  );
}
