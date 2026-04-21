import type { AuthSession } from '../types/auth';

const AUTH_STORAGE_KEY = 'intervai.auth.session';

export function getStoredAuthSession(): AuthSession | null {
  if (typeof window === 'undefined') {
    return null;
  }

  const raw = window.localStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as AuthSession;
  } catch {
    return null;
  }
}

export function setStoredAuthSession(session: AuthSession): void {
  if (typeof window === 'undefined') {
    return;
  }

  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
}

export function clearStoredAuthSession(): void {
  if (typeof window === 'undefined') {
    return;
  }

  window.localStorage.removeItem(AUTH_STORAGE_KEY);
}

export function getAuthToken(): string | null {
  return getStoredAuthSession()?.token ?? null;
}
