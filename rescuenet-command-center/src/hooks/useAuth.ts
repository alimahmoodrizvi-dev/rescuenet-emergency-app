import { useCallback, useState } from "react";
import { api, clearSession, getRole, getToken, setSession } from "../api/client";

export function useAuth() {
  const [token, setToken] = useState<string | null>(getToken());
  const [role, setRole] = useState<string | null>(getRole());

  const login = useCallback(async (username: string, password: string) => {
    const response = await api.orgLogin(username, password);
    setSession(response.access_token, response.role);
    setToken(response.access_token);
    setRole(response.role);
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setToken(null);
    setRole(null);
  }, []);

  return { token, role, isAuthenticated: !!token, login, logout };
}
