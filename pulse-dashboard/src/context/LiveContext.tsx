import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Client, type StompSubscription } from '@stomp/stompjs';
import { refreshSession } from '../api/client';
import { tokens } from '../api/tokens';
import { useAuth } from './AuthContext';

type Listener = (body: unknown) => void;

interface LiveContextType {
  connected: boolean;
  /** Subscribes to a STOMP topic; returns an unsubscribe function. Survives reconnects. */
  subscribe: (destination: string, listener: Listener) => () => void;
}

const LiveContext = createContext<LiveContextType>({ connected: false, subscribe: () => () => {} });

function brokerUrl() {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
  // Spring's SockJS endpoint also serves a raw WebSocket at /websocket.
  return `${scheme}://${window.location.host}/ws-pulse/websocket`;
}

interface Registration {
  destination: string;
  listener: Listener;
  stomp?: StompSubscription;
}

/**
 * One shared STOMP connection for the signed-in user. The access token is sent on CONNECT (the
 * server authorises every subscription); if the server rejects it, the session is refreshed and
 * the client reconnects with the new token.
 */
export const LiveProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated } = useAuth();
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);
  const registrations = useRef(new Map<number, Registration>());
  const nextId = useRef(0);

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }

    const attach = (client: Client, registration: Registration) => {
      registration.stomp = client.subscribe(registration.destination, message => {
        try {
          registration.listener(JSON.parse(message.body));
        } catch {
          // ignore malformed payloads
        }
      });
    };

    const client = new Client({
      brokerURL: brokerUrl(),
      reconnectDelay: 5000,
      beforeConnect: async () => {
        client.connectHeaders = { Authorization: `Bearer ${tokens.access() ?? ''}` };
      },
      onConnect: () => {
        setConnected(true);
        registrations.current.forEach(registration => attach(client, registration));
      },
      onStompError: () => {
        // Most likely an expired access token on CONNECT; refresh before the automatic reconnect.
        void refreshSession();
      },
      onWebSocketClose: () => setConnected(false),
    });

    clientRef.current = client;
    client.activate();
    return () => {
      clientRef.current = null;
      setConnected(false);
      void client.deactivate();
    };
  }, [isAuthenticated]);

  const subscribe = useCallback((destination: string, listener: Listener) => {
    const id = nextId.current++;
    const registration: Registration = { destination, listener };
    registrations.current.set(id, registration);
    const client = clientRef.current;
    if (client?.connected) {
      registration.stomp = client.subscribe(destination, message => {
        try {
          registration.listener(JSON.parse(message.body));
        } catch {
          // ignore malformed payloads
        }
      });
    }
    return () => {
      registrations.current.delete(id);
      if (clientRef.current?.connected) {
        registration.stomp?.unsubscribe();
      }
    };
  }, []);

  return <LiveContext.Provider value={{ connected, subscribe }}>{children}</LiveContext.Provider>;
};

export const useLive = () => useContext(LiveContext);

/** Subscribes to {@code destination} while mounted (no-op when it is null). */
export function useTopic<T>(destination: string | null, onMessage: (body: T) => void) {
  const { subscribe } = useLive();
  const handler = useRef(onMessage);
  handler.current = onMessage;

  useEffect(() => {
    if (!destination) {
      return;
    }
    return subscribe(destination, body => handler.current(body as T));
  }, [destination, subscribe]);
}
