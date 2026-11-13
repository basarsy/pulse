// Token storage. localStorage keeps the session across reloads; the trade-off (readable by
// injected scripts) is accepted for this dashboard, and access tokens are short-lived.

const ACCESS = 'pulse.accessToken';
const REFRESH = 'pulse.refreshToken';

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

export const tokens = {
  access: () => read(ACCESS),
  refresh: () => read(REFRESH),
  set(accessToken: string, refreshToken: string) {
    localStorage.setItem(ACCESS, accessToken);
    localStorage.setItem(REFRESH, refreshToken);
  },
  clear() {
    localStorage.removeItem(ACCESS);
    localStorage.removeItem(REFRESH);
  },
};

/** Fired when the session cannot be recovered (refresh failed); the app returns to login. */
export const SESSION_EXPIRED_EVENT = 'pulse:session-expired';
