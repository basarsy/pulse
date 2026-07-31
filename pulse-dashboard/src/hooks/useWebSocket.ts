import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useNotifications } from '../context/NotificationContext';

export function useWebSocket() {
  const clientRef = useRef<Client | null>(null);
  const { addNotification } = useNotifications();

  useEffect(() => {
    const socket = new SockJS('http://localhost:8080/ws-pulse');
    const client = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      onConnect: () => {
        console.log('Connected to WS');
        // Example subscriptions, normally parameterized
        client.subscribe('/topic/vehicles/*/commands', (message) => {
          const body = JSON.parse(message.body);
          addNotification(`Command update: ${body.type} - ${body.status}`, 'info');
        });
        client.subscribe('/topic/rollouts/*', (message) => {
          const body = JSON.parse(message.body);
          addNotification(`Rollout update: ${body.status}`, 'success');
        });
      }
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, [addNotification]);

  return clientRef.current;
}
