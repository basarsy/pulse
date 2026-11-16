import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';
import Header from './Header';
import LiveNotifications from './LiveNotifications';
import styles from './Layout.module.css';

export default function Layout() {
  return (
    <div className={styles.layout}>
      <LiveNotifications />
      <Sidebar />
      <div className={styles.main}>
        <Header />
        <main className={styles.content}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
