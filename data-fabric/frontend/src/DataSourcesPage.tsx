import { PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { App as AntdApp, Button, Card, Col, Empty, Form, Input, InputNumber, Popconfirm, Row, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import * as api from "./api";
import type { DataSourceRequest, DataSourceView } from "./api";

const BUSINESS_TYPES = ["OceanBase", "Greenplum"];

const emptyForm: DataSourceRequest = {
  name: "",
  businessType: "OceanBase",
  host: "",
  port: 3306,
  database: "",
  user: "",
  password: "",
};

export default function DataSourcesPage({ isOperator = true }: { isOperator?: boolean }) {
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm<DataSourceRequest>();
  const [dataSources, setDataSources] = useState<DataSourceView[]>([]);
  const [loading, setLoading] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [testing, setTesting] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setDataSources(await api.listDataSources());
    } catch (e) {
      message.error("加载数据源失败：" + String(e));
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onSubmit(values: DataSourceRequest) {
    try {
      if (editingId === null) {
        await api.createDataSource(values);
        message.success("数据源已注册");
      } else {
        await api.updateDataSource(editingId, values);
        message.success("数据源已更新");
      }
      setEditingId(null);
      form.resetFields();
      await refresh();
    } catch (e) {
      message.error(String(e));
    }
  }

  async function onTest(values: DataSourceRequest) {
    setTesting(true);
    try {
      await api.testDataSourceConnection(values);
      message.success("连接成功");
    } catch (e) {
      message.error("连接失败：" + String(e));
    } finally {
      setTesting(false);
    }
  }

  async function onDisable(id: number) {
    try {
      await api.disableDataSource(id);
      message.success("数据源已停用");
      await refresh();
    } catch (e) {
      message.error(String(e));
    }
  }

  function onEdit(dataSource: DataSourceView) {
    setEditingId(dataSource.id);
    form.setFieldsValue({
      name: dataSource.name,
      businessType: dataSource.businessType,
      host: dataSource.host,
      port: dataSource.port,
      database: dataSource.database,
      user: dataSource.user,
      password: "",
    });
  }

  function onCancelEdit() {
    setEditingId(null);
    form.resetFields();
  }

  const columns: ColumnsType<DataSourceView> = [
    { title: "名称", dataIndex: "name" },
    { title: "类型", dataIndex: "businessType", render: (value: string) => <Tag color="blue">{value}</Tag> },
    {
      title: "地址",
      render: (_, record) => `${record.host}:${record.port}${record.database ? "/" + record.database : ""}`,
    },
    { title: "用户", dataIndex: "user" },
    {
      title: "状态",
      dataIndex: "enabled",
      render: (enabled: boolean) => <Tag color={enabled ? "green" : "default"}>{enabled ? "已启用" : "已停用"}</Tag>,
    },
    {
      title: "操作",
      width: 160,
      render: (_, record) =>
        isOperator && record.enabled ? (
          <Space>
            <Button type="link" size="small" onClick={() => onEdit(record)}>
              编辑
            </Button>
            <Popconfirm title="确认停用该数据源？" onConfirm={() => onDisable(record.id)} okText="停用" cancelText="取消">
              <Button type="link" size="small" danger>
                停用
              </Button>
            </Popconfirm>
          </Space>
        ) : null,
    },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <Card
        className="df-glass"
        variant="borderless"
        title="数据源"
        extra={
          <Button icon={<ReloadOutlined />} onClick={() => void refresh()}>
            刷新
          </Button>
        }
      >
        <Table<DataSourceView>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={dataSources}
          pagination={{ pageSize: 8, hideOnSinglePage: true }}
          locale={{ emptyText: <Empty description="暂无数据源" /> }}
        />
      </Card>

      {isOperator && (
        <Card className="df-glass" variant="borderless" title={editingId === null ? "新增数据源" : "编辑数据源"}>
          <Form
            form={form}
            layout="vertical"
            initialValues={emptyForm}
            onFinish={onSubmit}
            disabled={!isOperator}
            requiredMark={false}
          >
            <Row gutter={16}>
              <Col xs={24} md={8}>
                <Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
                  <Input placeholder="小写标识，如 ob_demo" disabled={editingId !== null} />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="businessType" label="业务类型" rules={[{ required: true }]}>
                  <Select
                    options={BUSINESS_TYPES.map((type) => ({ value: type, label: type }))}
                  />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="host" label="主机" rules={[{ required: true, message: "请输入主机" }]}>
                  <Input placeholder="数据库主机" />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="port" label="端口" rules={[{ required: true, message: "请输入端口" }]}>
                  <InputNumber min={1} max={65535} style={{ width: "100%" }} />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="database" label="数据库" tooltip="Greenplum 必填">
                  <Input placeholder="Greenplum 必填" />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="user" label="用户名" rules={[{ required: true, message: "请输入用户名" }]}>
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="password" label="密码">
                  <Input.Password placeholder={editingId === null ? "" : "留空表示不修改"} />
                </Form.Item>
              </Col>
            </Row>
            <Space>
              <Button type="primary" htmlType="submit">
                {editingId === null ? "注册" : "保存"}
              </Button>
              <Button
                loading={testing}
                onClick={() => {
                  form
                    .validateFields()
                    .then(onTest)
                    .catch(() => undefined);
                }}
              >
                测试连接
              </Button>
              {editingId !== null && <Button onClick={onCancelEdit}>取消</Button>}
            </Space>
          </Form>
        </Card>
      )}

      {!isOperator && (
        <Typography.Text type="secondary">仅运营管理员可管理数据源。</Typography.Text>
      )}
    </div>
  );
}
