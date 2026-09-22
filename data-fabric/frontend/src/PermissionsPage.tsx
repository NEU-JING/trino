import { App as AntdApp, Button, Card, Col, Empty, Form, Popconfirm, Row, Select, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import * as api from "./api";
import type { PermissionView, UserView } from "./api";
import CatalogSchemaTableSelect, { type TableTarget } from "./components/CatalogSchemaTableSelect";

interface GrantFormValues {
  principal: string;
  principalType: string;
  target: TableTarget;
  permission: string;
}

const initialValues: GrantFormValues = {
  principal: "",
  principalType: "USER",
  target: {},
  permission: "SELECT",
};

export default function PermissionsPage() {
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm<GrantFormValues>();
  const [permissions, setPermissions] = useState<PermissionView[]>([]);
  const [users, setUsers] = useState<UserView[]>([]);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setPermissions(await api.listPermissions());
    } catch (e) {
      message.error("加载授权失败：" + String(e));
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void refresh();
    api
      .listUsers()
      .then((result) => setUsers(result.filter((user) => user.enabled)))
      .catch(() => undefined);
  }, [refresh]);

  async function onGrant(values: GrantFormValues) {
    const permission: PermissionView = {
      principal: values.principal,
      principalType: values.principalType,
      catalog: values.target.catalog ?? "",
      schema: values.target.schema ?? "",
      table: values.target.table ?? "",
      permission: values.permission,
    };
    try {
      await api.grantPermission(permission);
      message.success("授权成功");
      form.resetFields();
      await refresh();
    } catch (e) {
      message.error(String(e));
    }
  }

  async function onRevoke(permission: PermissionView) {
    try {
      await api.revokePermission(permission);
      message.success("已回收授权");
      await refresh();
    } catch (e) {
      message.error(String(e));
    }
  }

  const columns: ColumnsType<PermissionView> = [
    { title: "主体", dataIndex: "principal" },
    { title: "类型", dataIndex: "principalType", width: 100, render: (value: string) => <Tag>{value}</Tag> },
    {
      title: "表",
      render: (_, record) => <Tag>{`${record.catalog}.${record.schema}.${record.table}`}</Tag>,
    },
    { title: "权限", dataIndex: "permission", width: 120, render: (value: string) => <Tag color="geekblue">{value}</Tag> },
    {
      title: "操作",
      width: 100,
      render: (_, record) => (
        <Popconfirm title="确认回收该授权？" onConfirm={() => onRevoke(record)} okText="回收" cancelText="取消">
          <Button type="link" size="small" danger>
            回收
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <Card className="df-glass" variant="borderless" title="权限管理">
        <Table<PermissionView>
          rowKey={(record) => `${record.principal}-${record.catalog}-${record.schema}-${record.table}-${record.permission}`}
          loading={loading}
          columns={columns}
          dataSource={permissions}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          locale={{ emptyText: <Empty description="暂无授权" /> }}
        />
      </Card>

      <Card className="df-glass" variant="borderless" title="授予表权限">
        <Form form={form} layout="vertical" initialValues={initialValues} onFinish={onGrant} requiredMark={false}>
          <Row gutter={16}>
            <Col xs={24} md={8}>
              <Form.Item name="principal" label="主体" rules={[{ required: true, message: "请选择用户" }]}>
                <Select
                  placeholder="选择用户"
                  options={users.map((user) => ({
                    value: user.username,
                    label: `${user.username}（${user.role}）`,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={4}>
              <Form.Item name="principalType" label="类型" rules={[{ required: true }]}>
                <Select options={[{ value: "USER", label: "USER" }, { value: "ROLE", label: "ROLE" }]} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                name="target"
                label="数据表（catalog / schema / table）"
                rules={[
                  {
                    validator: (_, value: TableTarget | undefined) =>
                      value?.catalog && value?.schema && value?.table
                        ? Promise.resolve()
                        : Promise.reject(new Error("请依次选择 catalog、schema 与 table")),
                  },
                ]}
              >
                <CatalogSchemaTableSelect />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="permission" label="权限" rules={[{ required: true }]}>
            <Select style={{ width: 160 }} options={[{ value: "SELECT", label: "SELECT" }]} />
          </Form.Item>
          <Button type="primary" htmlType="submit">
            授权
          </Button>
        </Form>
      </Card>
    </div>
  );
}
