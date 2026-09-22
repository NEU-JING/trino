import {
  ConsoleSqlOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  LogoutOutlined,
  MoonOutlined,
  SafetyCertificateOutlined,
  SunOutlined,
  TableOutlined,
  TeamOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { Avatar, Dropdown, Layout, Menu, Space, Switch, Tag, Typography } from "antd";
import type { MenuProps } from "antd";
import { Suspense } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import BrandBanner from "./BrandBanner";
import ErrorBoundary from "./ErrorBoundary";
import PageLoader from "./PageLoader";
import { useThemeMode } from "../theme";

const { Header, Sider, Content } = Layout;

interface Props {
  username: string;
  role: string;
  onLogout: () => void;
}

const ROLE_LABEL: Record<string, string> = {
  OPERATOR: "运营管理员",
  QUERY_USER: "查询用户",
};

export default function AppShell({ username, role, onLogout }: Props) {
  const navigate = useNavigate();
  const location = useLocation();
  const { mode, toggle } = useThemeMode();
  const isOperator = role === "OPERATOR";

  const allItems: MenuProps["items"] = [
    { key: "/", icon: <DashboardOutlined />, label: "总览" },
    { key: "/query", icon: <ConsoleSqlOutlined />, label: "SQL 查询" },
    { key: "/tables", icon: <TableOutlined />, label: "表目录" },
    ...(isOperator
      ? [
          { key: "/data-sources", icon: <DatabaseOutlined />, label: "数据源管理" },
          { key: "/permissions", icon: <SafetyCertificateOutlined />, label: "权限管理" },
          { key: "/users", icon: <TeamOutlined />, label: "用户管理" },
        ]
      : []),
  ];

  const selectedKey = location.pathname === "/" ? "/" : "/" + location.pathname.split("/")[1];

  const userMenu: MenuProps = {
    items: [
      {
        key: "identity",
        disabled: true,
        label: (
          <div>
            <div style={{ fontWeight: 600 }}>{username}</div>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {ROLE_LABEL[role] ?? role}
            </Typography.Text>
          </div>
        ),
      },
      { type: "divider" },
      { key: "logout", icon: <LogoutOutlined />, label: "退出登录", onClick: onLogout },
    ],
  };

  return (
    <Layout className="df-grid" style={{ minHeight: "100vh" }}>
      <Header style={{ height: "auto", padding: "16px 24px 8px", lineHeight: "normal" }}>
        <div
          className="df-glass df-banner"
          style={{
            borderRadius: 16,
            padding: "14px 20px",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 16,
          }}
        >
          <BrandBanner />
          <Space size="large" align="center">
            <Space size={6} align="center">
              <SunOutlined style={{ color: "var(--df-text-muted)" }} />
              <Switch checked={mode === "dark"} onChange={toggle} checkedChildren={<MoonOutlined />} unCheckedChildren={<SunOutlined />} />
            </Space>
            <Dropdown menu={userMenu} trigger={["click"]} placement="bottomRight">
              <Space style={{ cursor: "pointer" }} size={8}>
                <Avatar size="small" icon={<UserOutlined />} style={{ background: "var(--df-accent)" }} />
                <span style={{ fontWeight: 500 }}>{username}</span>
                <Tag color={isOperator ? "blue" : "green"} style={{ marginInlineEnd: 0 }}>
                  {ROLE_LABEL[role] ?? role}
                </Tag>
              </Space>
            </Dropdown>
          </Space>
        </div>
      </Header>

      <Layout>
        <Sider width={208} style={{ padding: "8px 12px 24px" }}>
          <div className="df-glass" style={{ borderRadius: 14, padding: 8, position: "sticky", top: 16 }}>
            <Menu
              className="df-menu"
              mode="inline"
              selectedKeys={[selectedKey]}
              items={allItems}
              onClick={({ key }) => navigate(key)}
            />
          </div>
        </Sider>
        <Content className="df-content" style={{ padding: "8px 24px 32px" }}>
          <ErrorBoundary>
            <Suspense fallback={<PageLoader />}>
              <Outlet />
            </Suspense>
          </ErrorBoundary>
        </Content>
      </Layout>
    </Layout>
  );
}
