import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Form, Input, Typography, App as AntdApp } from "antd";
import { lazy, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import * as api from "./api";
import AppShell from "./components/AppShell";
import BrandBanner from "./components/BrandBanner";

const DashboardPage = lazy(() => import("./DashboardPage"));
const DataSourcesPage = lazy(() => import("./DataSourcesPage"));
const DatasetsPage = lazy(() => import("./DatasetsPage"));
const PermissionsPage = lazy(() => import("./PermissionsPage"));
const QueryPage = lazy(() => import("./QueryPage"));
const TablesPage = lazy(() => import("./TablesPage"));
const UsersPage = lazy(() => import("./UsersPage"));

interface Session {
  token: string;
  username: string;
  role: string;
}

export default function App() {
  const [session, setSession] = useState<Session | null>(null);

  async function onLogin(username: string, password: string) {
    const result = await api.login(username, password);
    api.setToken(result.token);
    setSession({ token: result.token, username: result.username, role: result.role });
  }

  function logout() {
    api.setToken(null);
    setSession(null);
  }

  if (!session) {
    return <LoginPage onLogin={onLogin} />;
  }

  const isOperator = session.role === "OPERATOR";

  return (
    <Routes>
      <Route element={<AppShell username={session.username} role={session.role} onLogout={logout} />}>
        <Route index element={<DashboardPage isOperator={isOperator} />} />
        <Route path="query" element={<QueryPage />} />
        <Route path="tables" element={<TablesPage isOperator={isOperator} />} />
        <Route path="datasets" element={<DatasetsPage isOperator={isOperator} />} />
        <Route
          path="data-sources"
          element={isOperator ? <DataSourcesPage /> : <Navigate to="/" replace />}
        />
        <Route
          path="permissions"
          element={isOperator ? <PermissionsPage /> : <Navigate to="/" replace />}
        />
        <Route path="users" element={isOperator ? <UsersPage /> : <Navigate to="/" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

function LoginPage({ onLogin }: { onLogin: (username: string, password: string) => Promise<void> }) {
  const { message } = AntdApp.useApp();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  async function submit(values: { username: string; password: string }) {
    setSubmitting(true);
    setError("");
    try {
      await onLogin(values.username, values.password);
    } catch (e) {
      const text = e instanceof Error ? e.message : String(e);
      setError(text);
      message.error("登录失败：" + text);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      className="df-grid"
      style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
        position: "relative",
      }}
    >
      <Card className="df-glass" variant="borderless" style={{ width: 400, borderRadius: 18, position: "relative", zIndex: 1 }}>
        <div style={{ textAlign: "center", marginBottom: 24 }}>
          <BrandBanner />
        </div>
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
        <Form layout="vertical" onFinish={submit} requiredMark={false} initialValues={{ username: "", password: "" }}>
          <Form.Item name="username" rules={[{ required: true, message: "请输入用户名" }]}>
            <Input size="large" prefix={<UserOutlined />} placeholder="用户名" autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: "请输入密码" }]}>
            <Input.Password size="large" prefix={<LockOutlined />} placeholder="密码" autoComplete="current-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" size="large" htmlType="submit" block loading={submitting}>
              登录
            </Button>
          </Form.Item>
        </Form>
        <Typography.Paragraph type="secondary" style={{ textAlign: "center", marginTop: 16, marginBottom: 0, fontSize: 12 }}>
          原型账号：admin / admin · viewer / viewer
        </Typography.Paragraph>
      </Card>
    </div>
  );
}
