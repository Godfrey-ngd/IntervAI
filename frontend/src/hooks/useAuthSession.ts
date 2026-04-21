import { useCallback, useEffect, useState } from 'react';
import { authApi } from '../api/auth';
import type { AuthSession, LoginRequest, RegisterRequest } from '../types/auth';
import { clearStoredAuthSession, getStoredAuthSession, setStoredAuthSession } from '../utils/auth';

export function useAuthSession() {
  const [session, setSession] = useState<AuthSession | null>(() => getStoredAuthSession());
  const [loading, setLoading] = useState(true);

  const syncSession = useCallback((nextSession: AuthSession | null) => {
    if (nextSession) {
      setStoredAuthSession(nextSession);
    } else {
      clearStoredAuthSession();
    }
    setSession(nextSession);
  }, []);

  useEffect(() => {
    let cancelled = false;

    const hydrate = async () => {
      const stored = getStoredAuthSession();
      if (!stored) {
        if (!cancelled) {
          setSession(null);
          setLoading(false);
        }
        return;
      }

      try {
        const user = await authApi.me();
        if (cancelled) {
          return;
        }
        syncSession({
          ...stored,
          user,
        });
      } catch {
        if (!cancelled) {
          syncSession(null);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    hydrate();

    return () => {
      cancelled = true;
    };
  }, [syncSession]);

  const login = useCallback(async (request: LoginRequest) => {
    const nextSession = await authApi.login(request);
    syncSession(nextSession);
    return nextSession;
  }, [syncSession]);

  const register = useCallback(async (request: RegisterRequest) => {
    const nextSession = await authApi.register(request);
    syncSession(nextSession);
    return nextSession;
  }, [syncSession]);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // Logout is best-effort only for the skeleton flow.
    } finally {
      syncSession(null);
    }
  }, [syncSession]);

  const refresh = useCallback(async () => {
    if (!getStoredAuthSession()) {
      syncSession(null);
      return null;
    }

    try {
      const stored = getStoredAuthSession();
      const user = await authApi.me();
      if (!stored) {
        syncSession(null);
        return null;
      }
      const nextSession = { ...stored, user };
      syncSession(nextSession);
      return nextSession;
    } catch {
      syncSession(null);
      return null;
    }
  }, [syncSession]);

  return {
    session,
    user: session?.user ?? null,
    isAuthenticated: Boolean(session),
    loading,
    login,
    register,
    logout,
    refresh,
  };
}
