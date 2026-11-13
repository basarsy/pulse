import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { authApi } from '../api/endpoints';
import { SESSION_EXPIRED_EVENT, tokens } from '../api/tokens';
import { ADMIN_ROLES, type AuthResponse, type User } from '../api/types';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  /** True while restoring a session from stored tokens on page load. */
  restoring: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType>({} as AuthContextType);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [restoring, setRestoring] = useState<boolean>(!!tokens.access());

  const startSession = useCallback((auth: AuthResponse) => {
    tokens.set(auth.accessToken, auth.refreshToken);
    setUser(auth.user);
  }, []);

  const logout = useCallback(() => {
    tokens.clear();
    setUser(null);
  }, []);

  // Restore the session after a reload (the API client refreshes an expired access token).
  useEffect(() => {
    if (!tokens.access()) {
      return;
    }
    authApi.me()
      .then(setUser)
      .catch(() => logout())
      .finally(() => setRestoring(false));
  }, [logout]);

  useEffect(() => {
    const onExpired = () => setUser(null);
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    startSession(await authApi.login(email, password));
  }, [startSession]);

  const register = useCallback(async (fullName: string, email: string, password: string) => {
    startSession(await authApi.register(fullName, email, password));
  }, [startSession]);

  const isAdmin = !!user?.roles.some(role => ADMIN_ROLES.includes(role));

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, isAdmin, restoring, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
