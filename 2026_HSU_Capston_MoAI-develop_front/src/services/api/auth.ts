import type { ApiResponse, RegisterRequest, RegisterResponse, LoginRequest, LoginResponse, LogoutRequest, RefreshTokenRequest, RefreshTokenResponse } from '../../types/api'
import { api, unwrap } from './helpers'

export async function register(body: RegisterRequest): Promise<RegisterResponse> {
  return unwrap(api.post<ApiResponse<RegisterResponse>>('/api/auth/register', body))
}

export async function login(body: LoginRequest): Promise<LoginResponse> {
  return unwrap(api.post<ApiResponse<LoginResponse>>('/api/auth/login', body))
}

export async function logout(body: LogoutRequest): Promise<void> {
  await api.post('/api/auth/logout', body)
}

export async function refreshAccessToken(body: RefreshTokenRequest): Promise<RefreshTokenResponse> {
  return unwrap(api.post<ApiResponse<RefreshTokenResponse>>('/api/auth/refresh', body))
}
