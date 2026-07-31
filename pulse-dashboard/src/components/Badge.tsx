import React from 'react';

interface BadgeProps {
  children: React.ReactNode;
  color?: 'green' | 'red' | 'yellow' | 'blue' | 'gray';
}

export default function Badge({ children, color = 'gray' }: BadgeProps) {
  const colors = {
    green: { bg: 'rgba(16, 185, 129, 0.2)', text: '#10b981' },
    red: { bg: 'rgba(239, 68, 68, 0.2)', text: '#ef4444' },
    yellow: { bg: 'rgba(245, 158, 11, 0.2)', text: '#f59e0b' },
    blue: { bg: 'rgba(59, 130, 246, 0.2)', text: '#3b82f6' },
    gray: { bg: 'rgba(148, 163, 184, 0.2)', text: '#94a3b8' }
  };

  return (
    <span style={{
      display: 'inline-block',
      padding: '0.25rem 0.75rem',
      borderRadius: 'var(--radius-full)',
      fontSize: '0.75rem',
      fontWeight: 600,
      backgroundColor: colors[color].bg,
      color: colors[color].text,
      textTransform: 'uppercase',
      letterSpacing: '0.05em'
    }}>
      {children}
    </span>
  );
}
