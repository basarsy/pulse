import { useCallback, useEffect, useRef, useState } from 'react';

interface AsyncState<T> {
  data: T | undefined;
  error: string | null;
  loading: boolean;
  reload: () => void;
  setData: React.Dispatch<React.SetStateAction<T | undefined>>;
}

/**
 * Loads data on mount and whenever {@code deps} change; optionally refreshes every
 * {@code pollMs} milliseconds (for state that is not pushed over WebSocket).
 */
export function useAsync<T>(load: () => Promise<T>, deps: unknown[], pollMs?: number): AsyncState<T> {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const loader = useRef(load);
  loader.current = load;

  const reload = useCallback(() => {
    loader.current()
      .then(result => {
        setData(result);
        setError(null);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    setLoading(true);
    reload();
    if (!pollMs) {
      return;
    }
    const timer = window.setInterval(reload, pollMs);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, pollMs]);

  return { data, error, loading, reload, setData };
}
