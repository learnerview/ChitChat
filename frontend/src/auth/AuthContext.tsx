import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { api, clearStoredAuth, loadStoredAuth } from '../api/client';
import type { AuthResponse } from '../api/types';

interface AuthContextValue {
  auth: AuthResponse | null;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, displayName: string, password: string) => Promise<void>;
  logout: () => void;
  switchWorkspace: (tenantId: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<AuthResponse | null>(() => loadStoredAuth());

  const login = useCallback(async (username: string, password: string) => {
    setAuth(await api.login(username, password));
  }, []);

  const register = useCallback(
    async (username: string, displayName: string, password: string) => {
      await api.register(username, displayName, password);
      await login(username, password);
    },
    [login],
  );

  const logout = useCallback(() => {
    clearStoredAuth();
    setAuth(null);
  }, []);

  const switchWorkspace = useCallback(async (tenantId: string) => {
    setAuth(await api.switchWorkspace(tenantId));
  }, []);

  const value = useMemo(
    () => ({ auth, login, register, logout, switchWorkspace }),
    [auth, login, register, logout, switchWorkspace],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
