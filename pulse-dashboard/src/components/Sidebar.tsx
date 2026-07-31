import React from 'react';
import { Link, useLocation } from 'react-router-dom';

export default function Sidebar() {
  const location = useLocation();
  const links = [
    { path: '/', label: 'Dashboard', icon: '📊' },
    { path: '/vehicles', label: 'Vehicles', icon: '🚗' },
    { path: '/rollouts', label: 'OTA Updates', icon: '☁️' }
  ];

  return (
    <aside style={{
      width: '250px',
      backgroundColor: 'var(--color-bg-secondary)',
      borderRight: '1px solid var(--color-border)',
      display: 'flex',
      flexDirection: 'column',
      padding: '1.5rem 1rem'
    }}>
      <div style={{ padding: '0 1rem 2rem' }}>
        <h2 className="text-gradient" style={{ margin: 0, fontSize: '1.5rem' }}>⚡ PULSE</h2>
      </div>
      
      <nav style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
        {links.map(link => {
          const isActive = location.pathname === link.path || (link.path !== '/' && location.pathname.startsWith(link.path));
          return (
            <Link key={link.path} to={link.path} style={{
              display: 'flex',
              alignItems: 'center',
              gap: '1rem',
              padding: '0.75rem 1rem',
              borderRadius: 'var(--radius-md)',
              backgroundColor: isActive ? 'rgba(59, 130, 246, 0.1)' : 'transparent',
              color: isActive ? 'var(--color-accent-primary)' : 'var(--color-text-secondary)',
              fontWeight: isActive ? 600 : 400,
            }}>
              <span style={{ fontSize: '1.2rem' }}>{link.icon}</span>
              {link.label}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
