import React, { useState, useCallback } from 'react';
import Toast, { ToastStack } from '../components/Toast';
import { NotificationContext, type Notification } from './notifications';

export const NotificationProvider: React.FC<{children: React.ReactNode}> = ({ children }) => {
  const [notifications, setNotifications] = useState<Notification[]>([]);

  const addNotification = useCallback((message: string, type: Notification['type'] = 'info') => {
    const newNotif: Notification = {
      id: Math.random().toString(36).substr(2, 9),
      message,
      type,
      timestamp: new Date(),
      read: false
    };
    setNotifications(prev => [newNotif, ...prev]);
  }, []);

  const markAsRead = (id: string) => {
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
  };

  const markAllAsRead = () => {
    setNotifications(prev => prev.map(n => ({ ...n, read: true })));
  };

  const unreadCount = notifications.filter(n => !n.read).length;

  return (
    <NotificationContext.Provider value={{ notifications, unreadCount, addNotification, markAsRead, markAllAsRead }}>
      {children}
      <ToastStack>
        {notifications.filter(n => !n.read).slice(0, 3).map(n => (
          <Toast key={n.id} notification={n} onClose={() => markAsRead(n.id)} />
        ))}
      </ToastStack>
    </NotificationContext.Provider>
  );
};
