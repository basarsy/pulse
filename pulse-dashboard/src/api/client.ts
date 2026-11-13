import { SESSION_EXPIRED_EVENT, tokens } from './tokens';
import type { AuthResponse } from './types';

// Same-origin: proxied by Vite in dev, by nginx in Docker and by the ingress in Kubernetes.
export const API_URL = '/api/v1';

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

let refreshInFlight: Promise<boolean> | null = null;

/**
 * Exchanges the refresh token for a new token pair. Concurrent callers share one request, so a
 * burst of 401s (several requests started with the same expired token) refreshes only once.
 */
export function refreshSession(): Promise<boolean> {
  const refreshToken = tokens.refresh();
  if (!refreshToken) {
    return Promise.resolve(false);
  }
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const response = await fetch(`${API_URL}/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        });
        if (!response.ok) {
          return false;
        }
        const body = (await response.json()) as AuthResponse;
        tokens.set(body.accessToken, body.refreshToken);
        return true;
      } catch {
        return false;
      }
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

function endSession() {
  tokens.clear();
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const accessToken = tokens.access();
  return fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...init.headers,
    },
  });
}

/**
 * Calls the API with the current access token. On 401 the session is refreshed once and the
 * request retried; if that is not possible the session ends.
 */
export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response = await send(path, init);

  if (response.status === 401 && tokens.refresh()) {
    if (await refreshSession()) {
      response = await send(path, init);
    }
  }
  if (response.status === 401 && tokens.access()) {
    endSession();
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new ApiError(response.status, body.message || `Request failed (${response.status})`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};
