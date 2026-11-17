import { useCallback, useEffect, useRef, useState } from 'react';

interface AsyncState<T> {
  data: T | undefined;
  error: string | null;
  loading: boolean;
  reload: () => void;
  setData: React.Dispatch<React.SetStateAction<T | undefined>>;
}

interface AsyncOptions<T> {
  /** Refresh every this many milliseconds (for state that is not pushed over WebSocket). */
  pollMs?: number;
  /**
   * Combines a load with what is shown, instead of replacing it. Pages that also receive live
   * updates pass a version-aware merge (utils/versions), so a load that read the database before
   * an update was committed can't overwrite that update when it resolves after it.
   */
  merge?: (shown: T | undefined, loaded: T) => T;
}

/** Loads data on mount and whenever {@code deps} change. */
export function useAsync<T>(load: () => Promise<T>, deps: unknown[], options: AsyncOptions<T> = {}): AsyncState<T> {
  const { pollMs } = options;
  const [data, setData] = useState<T>();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const loader = useRef(load);
  loader.current = load;
  const merger = useRef(options.merge);
  merger.current = options.merge;

  const reload = useCallback(() => {
    loader.current()
      .then(result => {
        const merge = merger.current;
        setData(shown => (merge ? merge(shown, result) : result));
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
