import React from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';
import Header from './Header';
import { useWebSocket } from '../hooks/useWebSocket';

export default function Layout() {
  // Initialize websocket globally
  useWebSocket();

  return (
    <div className="layout-container">
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
