import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';
import Header from './Header';
import LiveNotifications from './LiveNotifications';

export default function Layout() {
  return (
    <div className="layout-container">
      <LiveNotifications />
      <Sidebar />
      <main className="main-content">
        <Header />
        <div className="page-content">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
