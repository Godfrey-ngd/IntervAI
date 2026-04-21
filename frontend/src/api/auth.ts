import { request } from './request';
import type { AuthSession, AuthUser, LoginRequest, RegisterRequest } from '../types/auth';

export const authApi = {
  async login(data: LoginRequest): Promise<AuthSession> {
    return request.post<AuthSession>('/api/auth/login', data);
  },

  async register(data: RegisterRequest): Promise<AuthSession> {
    return request.post<AuthSession>('/api/auth/register', data);
  },

  async me(): Promise<AuthUser> {
    return request.get<AuthUser>('/api/auth/me');
  },

  async logout(): Promise<void> {
    await request.post<void>('/api/auth/logout');
  },
};
