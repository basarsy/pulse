import React, { useCallback, useEffect, useState } from 'react';
import { authApi } from '../api/endpoints';
import { SESSION_EXPIRED_EVENT, tokens } from '../api/tokens';
import { ADMIN_ROLES, type AuthResponse, type User } from '../api/types';
import { AuthContext } from './auth';

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
