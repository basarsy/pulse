import { createContext, useContext, useEffect, useRef } from 'react';

export type Listener = (body: unknown) => void;

export interface LiveContextType {
  connected: boolean;
  /** Subscribes to a STOMP topic; returns an unsubscribe function. Survives reconnects. */
  subscribe: (destination: string, listener: Listener) => () => void;
}

export const LiveContext = createContext<LiveContextType>({ connected: false, subscribe: () => () => {} });

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
