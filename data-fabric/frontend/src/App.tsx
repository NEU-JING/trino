import { useState } from "react";
import * as api from "./api";
import DataSourcesPage from "./DataSourcesPage";
import PermissionsPage from "./PermissionsPage";
import QueryPage from "./QueryPage";
import TablesPage from "./TablesPage";
import { input } from "./styles";

type View = "query" | "tables" | "data-sources" | "permissions";

export default function App() {
  const [token, setToken] = useState<string | null>(null);
  const [username, setUsername] = useState("");
  const [role, setRole] = useState("");
  const [view, setView] = useState<View>("tables");
  const [error, setError] = useState("");

  async function onLogin(usernameValue: string, password: string) {
    try {
      const result = await api.login(usernameValue, password);
      api.setToken(result.token);
      setToken(result.token);
      setUsername(result.username);
      setRole(result.role);
      setError("");
    } catch (e) {
      setError(String(e));
    }
  }

  function logout() {
    api.setToken(null);
    setToken(null);
    setUsername("");
    setRole("");
  }

  if (!token) {
    return <LoginForm onLogin={onLogin} error={error} />;
  }

  const isOperator = role === "OPERATOR";

  return (
    <main style={{ fontFamily: "sans-serif", padding: 24, maxWidth: 1100, margin: "0 auto" }}>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1>数据编织平台</h1>
        <div>
          {username}（{role}）
          <button onClick={logout} style={{ marginLeft: 8 }}>
            退出
          </button>
        </div>
      </header>

      <nav style={{ marginBottom: 16 }}>
        <button
          onClick={() => setView("query")}
          disabled={view === "query"}
          style={{ marginRight: 8 }}
        >
          SQL 查询
        </button>
        <button
          onClick={() => setView("tables")}
          disabled={view === "tables"}
          style={{ marginRight: 8 }}
        >
          表目录
        </button>
        {isOperator && (
          <button onClick={() => setView("data-sources")} disabled={view === "data-sources"} style={{ marginRight: 8 }}>
            数据源管理
          </button>
        )}
        {isOperator && (
          <button onClick={() => setView("permissions")} disabled={view === "permissions"}>
            权限管理
          </button>
        )}
      </nav>

      {view === "query" && <QueryPage />}
      {view === "tables" && <TablesPage isOperator={isOperator} />}
      {view === "data-sources" && <DataSourcesPage isOperator={isOperator} />}
      {view === "permissions" && <PermissionsPage />}
    </main>
  );
}

function LoginForm({ onLogin, error }: { onLogin: (username: string, password: string) => void; error: string }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  return (
    <main style={{ fontFamily: "sans-serif", padding: 48, maxWidth: 320, margin: "0 auto" }}>
      <h1>数据编织平台</h1>
      {error && <p style={{ color: "crimson" }}>{error}</p>}
      <input style={input} placeholder="用户名" value={username} onChange={(e) => setUsername(e.target.value)} />
      <input
        style={{ ...input, marginTop: 8 }}
        type="password"
        placeholder="密码"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      <button style={{ marginTop: 8 }} onClick={() => onLogin(username, password)}>
        登录
      </button>
    </main>
  );
}
