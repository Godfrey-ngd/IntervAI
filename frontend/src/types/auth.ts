export interface AuthUser {
  id: number;
  username: string;
  email: string;
  displayName: string;
  role: string;
  enabled: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface AuthSession {
  token: string;
  tokenType: string;
  user: AuthUser;
}

export interface LoginRequest {
  account: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  displayName: string;
}
