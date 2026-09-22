import { PlusOutlined } from "@ant-design/icons";
import { App as AntdApp, Button, Card, Empty, Form, Input, Modal, Select, Space, Switch, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import * as api from "./api";
import type { UserView } from "./api";

interface CreateUserValues {
  username: string;
  password: string;
  role: string;
}

const ROLE_OPTIONS = [
  { value: "OPERATOR", label: "运营管理员" },
  { value: "QUERY_USER", label: "查询用户" },
];

export default function UsersPage() {
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm<CreateUserValues>();
  const [users, setUsers] = useState<UserView[]>([]);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setUsers(await api.listUsers());
    } catch (e) {
      message.error("加载用户失败：" + String(e));
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onCreate(values: CreateUserValues) {
    setCreating(true);
    try {
      await api.createUser(values);
      message.success("用户已创建");
      setModalOpen(false);
      form.resetFields();
      await refresh();
    } catch (e) {
      message.error(String(e));
    } finally {
      setCreating(false);
    }
  }

  async function onToggle(user: UserView, enabled: boolean) {
    try {
      await api.updateUser(user.username, { enabled });
      message.success(enabled ? "用户已启用" : "用户已停用");
      await refresh();
    } catch (e) {
      message.error(String(e));
    }
  }

  async function onRole(user: UserView, role: string) {
    try {
      await api.updateUser(user.username, { role });
      message.success("角色已更新");
      await refresh();
    } catch (e) {
      message.error(String(e));
    }
  }

  const columns: ColumnsType<UserView> = [
    { title: "用户名", dataIndex: "username" },
    {
      title: "角色",
      dataIndex: "role",
      width: 220,
      render: (role: string, record) => (
        <Select
          value={role}
          style={{ width: 160 }}
          options={ROLE_OPTIONS}
          onChange={(value) => onRole(record, value)}
        />
      ),
    },
    {
      title: "状态",
      dataIndex: "enabled",
      width: 180,
      render: (enabled: boolean, record) => (
        <Space>
          <Switch checked={enabled} onChange={(checked) => onToggle(record, checked)} />
          <Tag color={enabled ? "green" : "default"}>{enabled ? "已启用" : "已停用"}</Tag>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <Card
        className="df-glass"
        variant="borderless"
        title="用户管理"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
            新增用户
          </Button>
        }
      >
        <Table<UserView>
          rowKey="username"
          loading={loading}
          columns={columns}
          dataSource={users}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          locale={{ emptyText: <Empty description="暂无用户" /> }}
        />
      </Card>

      <Modal
        title="新增用户"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => {
          form
            .validateFields()
            .then(onCreate)
            .catch(() => undefined);
        }}
        confirmLoading={creating}
        okText="创建"
        cancelText="取消"
      >
        <Form form={form} layout="vertical" initialValues={{ role: "QUERY_USER" }} requiredMark={false}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: "请输入用户名" }]}>
            <Input placeholder="登录用户名" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: "请输入密码" }]}>
            <Input.Password placeholder="初始密码" />
          </Form.Item>
          <Form.Item name="role" label="角色" rules={[{ required: true }]}>
            <Select options={ROLE_OPTIONS} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
