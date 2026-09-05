import { useState } from "react";
import { getBaseUrl, setBaseUrl } from "../api/client";
import { ApiError } from "../api/client";

export function LoginPage({ onLogin }: { onLogin: (username: string, password: string) => Promise<void> }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [backendUrl, setBackendUrlState] = useState(getBaseUrl());
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBaseUrl(backendUrl);
    setLoading(true);
    try {
      await onLogin(username, password);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError("Invalid credentials.");
      } else {
        setError("Couldn't reach the backend — check the URL below and that it's running.");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={handleSubmit}>
        <h1>RescueNet Command Center</h1>
        <p>Volunteer / Rescue Operator / Command Center Admin sign-in.</p>

        <label>Username</label>
        <input value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus />

        <label>Password</label>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />

        <label>Backend URL</label>
        <input value={backendUrl} onChange={(e) => setBackendUrlState(e.target.value)} placeholder="http://localhost:8000" />

        <button type="submit" className="btn btn-primary" style={{ width: "100%" }} disabled={loading}>
          {loading ? "Signing in…" : "Sign in"}
        </button>
        {error && <div className="error-text">{error}</div>}
        <p style={{ marginTop: 16, fontSize: 11 }}>
          No public self-signup exists for these roles by design (Part 24) — an admin
          provisions organizational accounts directly against the backend's database.
        </p>
      </form>
    </div>
  );
}
