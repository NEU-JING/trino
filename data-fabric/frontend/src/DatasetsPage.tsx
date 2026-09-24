import {
  ApartmentOutlined,
  PlusOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
} from "@ant-design/icons";
import {
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import ReactECharts from "echarts-for-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import * as api from "./api";
import type {
  CreateDatasetRequest,
  DatasetDetailView,
  DatasetFieldView,
  DatasetRelationView,
  DatasetUsageView,
  DatasetVersionView,
  DatasetView,
  LineageView,
  RelationGraphView,
} from "./api";
import { useThemeMode } from "./theme";

const KIND_COLOR: Record<string, string> = { BASE: "blue", DERIVED: "geekblue", AGGREGATE: "purple" };
const STATUS_COLOR: Record<string, string> = { DRAFT: "default", PUBLISHED: "green", DEPRECATED: "red" };
const ROLE_COLOR: Record<string, string> = {
  ID: "gold",
  DIMENSION: "cyan",
  MEASURE: "magenta",
  TIME: "orange",
  GEO: "lime",
  COMPUTED: "default",
};

interface CreateFormValues {
  name: string;
  description?: string;
  domain?: string;
  kind: string;
  baseTableId?: number;
  inputs?: string;
  projections?: string;
  filters?: string;
  measures?: string;
  groupBy?: string;
}

function lines(value?: string): string[] {
  return (value ?? "")
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

function toRequest(values: CreateFormValues): CreateDatasetRequest {
  const inputs = (values.inputs ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter((value) => value.length > 0);
  const measures = lines(values.measures).map((line) => {
    const [name, expression, aggregation] = line.split("=");
    return { name: name.trim(), expression: (expression ?? "").trim(), aggregation: (aggregation ?? "SUM").trim() };
  });
  return {
    name: values.name,
    description: values.description,
    domain: values.domain,
    kind: values.kind,
    baseTableId: values.kind === "BASE" ? values.baseTableId : undefined,
    inputs: values.kind === "BASE" ? undefined : inputs,
    projections: values.kind === "DERIVED" ? lines(values.projections) : undefined,
    filters: lines(values.filters),
    measures: values.kind === "AGGREGATE" ? measures : undefined,
    groupBy: values.kind === "AGGREGATE" ? lines(values.groupBy) : undefined,
  };
}

export default function DatasetsPage({ isOperator }: { isOperator: boolean }) {
  const { message } = AntdApp.useApp();
  const { mode } = useThemeMode();
  const [datasets, setDatasets] = useState<DatasetView[]>([]);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<DatasetDetailView | null>(null);
  const [versions, setVersions] = useState<DatasetVersionView[]>([]);
  const [lineage, setLineage] = useState<LineageView | null>(null);
  const [usage, setUsage] = useState<DatasetUsageView[]>([]);
  const [relations, setRelations] = useState<DatasetRelationView[]>([]);
  const [graph, setGraph] = useState<RelationGraphView | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<CreateFormValues>();
  const kind = Form.useWatch("kind", form);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [list, relationsList] = await Promise.all([api.listDatasets(), api.listRelations()]);
      setDatasets(list);
      setRelations(relationsList);
      if (isOperator) {
        setGraph(await api.relationGraph());
      }
    } catch (e) {
      message.error("加载数据集失败：" + String(e));
    } finally {
      setLoading(false);
    }
  }, [isOperator, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openDetail = useCallback(
    async (dataset: DatasetView) => {
      setDrawerOpen(true);
      setDetail(null);
      setVersions([]);
      setLineage(null);
      setUsage([]);
      try {
        const [detailResult, versionResult, lineageResult] = await Promise.all([
          api.getDataset(dataset.uid),
          api.listDatasetVersions(dataset.uid),
          api.datasetLineage(dataset.uid),
        ]);
        setDetail(detailResult);
        setVersions(versionResult);
        setLineage(lineageResult);
        if (isOperator) {
          setUsage(await api.datasetUsage(dataset.uid).catch(() => []));
        }
      } catch (e) {
        message.error("加载数据集详情失败：" + String(e));
      }
    },
    [isOperator, message],
  );

  async function onCreate(values: CreateFormValues) {
    setSubmitting(true);
    try {
      await api.createDataset(toRequest(values));
      message.success("数据集已保存为草稿");
      setCreateOpen(false);
      form.resetFields();
      await load();
    } catch (e) {
      message.error(String(e));
    } finally {
      setSubmitting(false);
    }
  }

  async function act(action: () => Promise<unknown>, success: string) {
    try {
      await action();
      message.success(success);
      await load();
      if (detail) {
        setDetail(await api.getDataset(detail.uid));
      }
    } catch (e) {
      message.error(String(e));
    }
  }

  async function onInfer() {
    try {
      const created = await api.inferRelations();
      message.success(`推断出 ${created.length} 条候选关系`);
      await load();
    } catch (e) {
      message.error(String(e));
    }
  }

  const columns: ColumnsType<DatasetView> = [
    { title: "名称", dataIndex: "name", render: (value: string, record) => (
      <Space>
        <Typography.Text strong>{value}</Typography.Text>
        <Tag color={KIND_COLOR[record.kind] ?? "default"}>{record.kind}</Tag>
      </Space>
    ) },
    { title: "域", dataIndex: "domain", width: 120, render: (value: string | null) => value || "-" },
    { title: "描述", dataIndex: "description", ellipsis: true },
    { title: "状态", dataIndex: "status", width: 110, render: (value: string) => <Tag color={STATUS_COLOR[value] ?? "default"}>{value}</Tag> },
    { title: "版本", dataIndex: "currentVersion", width: 80, render: (value: number) => `v${value}` },
    { title: "物化", dataIndex: "materializationMode", width: 120 },
    { title: "负责人", dataIndex: "owner", width: 120 },
    {
      title: "操作",
      width: 260,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          {isOperator && (
            <>
              <Button type="link" size="small" onClick={() => void act(() => api.publishDataset(record.uid), "已发布")}>
                发布
              </Button>
              <Button type="link" size="small" onClick={() => void act(() => api.refreshDatasetMaterialization(record.uid), "已刷新")}>
                刷新
              </Button>
              <Popconfirm title="确认弃用该数据集？" onConfirm={() => void act(() => api.deprecateDataset(record.uid), "已弃用")}>
                <Button type="link" size="small" danger>
                  弃用
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ];

  const fieldColumns: ColumnsType<DatasetFieldView> = [
    { title: "字段", dataIndex: "name", render: (value: string) => <Typography.Text code>{value}</Typography.Text> },
    { title: "类型", dataIndex: "dataType", render: (value: string | null) => value || "-" },
    { title: "语义角色", dataIndex: "role", render: (value: string) => <Tag color={ROLE_COLOR[value] ?? "default"}>{value}</Tag> },
    { title: "聚合", dataIndex: "aggregation", render: (value: string | null) => value || "-" },
    { title: "时间粒度", dataIndex: "timeGrain", render: (value: string | null) => value || "-" },
    { title: "可空", dataIndex: "nullable", render: (value: boolean) => (value ? "是" : "否") },
  ];

  const relationColumns: ColumnsType<DatasetRelationView> = [
    { title: "来源字段", render: (_, r) => <Typography.Text code>{r.fromField}</Typography.Text> },
    { title: "目标字段", render: (_, r) => <Typography.Text code>{r.toField}</Typography.Text> },
    { title: "Join", dataIndex: "joinType", width: 90 },
    { title: "基数", dataIndex: "cardinality", width: 90 },
    {
      title: "来源",
      dataIndex: "origin",
      width: 120,
      render: (value: string) =>
        value === "DECLARED" ? <Tag color="green">已声明</Tag> : <Tag color="orange">推断</Tag>,
    },
    {
      title: "操作",
      width: 100,
      render: (_, r) =>
        isOperator && r.origin !== "DECLARED" ? (
          <Button type="link" size="small" onClick={() => void act(() => api.confirmRelation(r.id), "已确认")}>
            确认
          </Button>
        ) : null,
    },
  ];

  const usageColumns: ColumnsType<DatasetUsageView> = [
    { title: "应用", dataIndex: "application" },
    { title: "用户", dataIndex: "username" },
    { title: "查询次数", dataIndex: "queryCount", width: 100 },
    { title: "失败", dataIndex: "failureCount", width: 80 },
    { title: "平均延迟(ms)", dataIndex: "averageLatencyMs", width: 130, render: (value: number) => value.toFixed(1) },
  ];

  const graphOption = useMemo(() => {
    const labelColor = mode === "dark" ? "#e6f0ff" : "#0b1f3a";
    const nodes = (graph?.nodes ?? []).map((node) => ({
      id: node.uid,
      name: node.name,
      symbolSize: 34,
      itemStyle: { color: node.kind === "AGGREGATE" ? "#722ed1" : node.kind === "DERIVED" ? "#2f54eb" : "#1668dc" },
    }));
    const edges = (graph?.edges ?? []).map((edge) => ({
      source: edge.fromDatasetUid,
      target: edge.toDatasetUid,
      lineStyle: { type: edge.origin === "DECLARED" ? "solid" : "dashed" },
    }));
    return {
      tooltip: {},
      series: [
        {
          type: "graph",
          layout: "force",
          roam: true,
          draggable: true,
          force: { repulsion: 240, edgeLength: 110, gravity: 0.08 },
          label: { show: true, color: labelColor, fontSize: 12 },
          data: nodes,
          edges,
        },
      ],
    };
  }, [graph, mode]);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <Card
        className="df-glass"
        variant="borderless"
        title="数据集"
        extra={
          isOperator && (
            <Space>
              <Button icon={<ThunderboltOutlined />} onClick={() => void onInfer()}>
                推断关系
              </Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
                新建数据集
              </Button>
            </Space>
          )
        }
      >
        <Table<DatasetView>
          rowKey="uid"
          loading={loading}
          columns={columns}
          dataSource={datasets}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          locale={{ emptyText: <Empty description="暂无数据集" /> }}
        />
      </Card>

      {isOperator && (
        <Card className="df-glass" variant="borderless" title={<span><ApartmentOutlined /> 关系图</span>}>
          {graph && graph.nodes.length > 0 ? (
            <ReactECharts option={graphOption} style={{ height: 380 }} notMerge />
          ) : (
            <Empty description="暂无关系数据" />
          )}
        </Card>
      )}

      <Card className="df-glass" variant="borderless" title="关系清单">
        <Table<DatasetRelationView>
          rowKey="id"
          size="small"
          columns={relationColumns}
          dataSource={relations}
          pagination={{ pageSize: 8, hideOnSinglePage: true }}
          locale={{ emptyText: <Empty description="暂无关系，可点击“推断关系”生成候选" /> }}
        />
      </Card>

      <Drawer
        width={720}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        title={detail ? `${detail.name}（${detail.kind}）` : "数据集详情"}
      >
        {detail && (
          <Space direction="vertical" size="large" style={{ width: "100%" }}>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="稳定标识" span={2}>{detail.uid}</Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>{detail.description || "-"}</Descriptions.Item>
              <Descriptions.Item label="域">{detail.domain || "-"}</Descriptions.Item>
              <Descriptions.Item label="负责人">{detail.owner}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={STATUS_COLOR[detail.status] ?? "default"}>{detail.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="版本">v{detail.currentVersion}</Descriptions.Item>
              <Descriptions.Item label="物化模式">{detail.materializationMode}</Descriptions.Item>
              <Descriptions.Item label="最近刷新">
                {detail.materialization?.refreshedAt ? new Date(detail.materialization.refreshedAt).toLocaleString() : "-"}
              </Descriptions.Item>
            </Descriptions>

            <div>
              <Typography.Title level={5}>字段</Typography.Title>
              <Table<DatasetFieldView>
                size="small"
                rowKey="name"
                pagination={false}
                columns={fieldColumns}
                dataSource={detail.fields}
                locale={{ emptyText: <Empty description="（无字段）" /> }}
              />
            </div>

            <div>
              <Typography.Title level={5}>版本历史</Typography.Title>
              <Table<DatasetVersionView>
                size="small"
                rowKey="version"
                pagination={false}
                columns={[
                  { title: "版本", dataIndex: "version", render: (value: number) => `v${value}` },
                  { title: "状态", dataIndex: "status", render: (value: string) => <Tag color={STATUS_COLOR[value] ?? "default"}>{value}</Tag> },
                  { title: "发布时间", dataIndex: "publishedAt", render: (value: string | null) => (value ? new Date(value).toLocaleString() : "-") },
                ]}
                dataSource={versions}
                locale={{ emptyText: <Empty description="（尚未发布）" /> }}
              />
            </div>

            {lineage && (
              <div>
                <Typography.Title level={5}>血缘</Typography.Title>
                <Descriptions column={1} size="small" bordered>
                  <Descriptions.Item label="上游">
                    {lineage.upstream.length
                      ? lineage.upstream.map((node) => <Tag key={node.id}>{node.label}</Tag>)
                      : "-"}
                  </Descriptions.Item>
                  <Descriptions.Item label="下游">
                    {lineage.downstream.length
                      ? lineage.downstream.map((node) => <Tag key={node.id}>{node.label}</Tag>)
                      : "-"}
                  </Descriptions.Item>
                </Descriptions>
              </div>
            )}

            {isOperator && (
              <div>
                <Typography.Title level={5}>用量</Typography.Title>
                <Table<DatasetUsageView>
                  size="small"
                  rowKey={(record) => `${record.application}-${record.username}`}
                  pagination={false}
                  columns={usageColumns}
                  dataSource={usage}
                  locale={{ emptyText: <Empty description="（暂无用量）" /> }}
                />
              </div>
            )}
          </Space>
        )}
      </Drawer>

      <Modal
        title="新建数据集"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={submitting}
        width={640}
      >
        <Form form={form} layout="vertical" onFinish={onCreate} initialValues={{ kind: "BASE" }}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称（小写字母/数字/下划线）" }]}>
            <Input placeholder="例如 monthly_dept_sales" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input />
          </Form.Item>
          <Form.Item name="domain" label="域">
            <Input placeholder="例如 finance" />
          </Form.Item>
          <Form.Item name="kind" label="类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: "BASE", label: "BASE · 基表直通" },
                { value: "DERIVED", label: "DERIVED · join/投影/过滤" },
                { value: "AGGREGATE", label: "AGGREGATE · 度量+维度" },
              ]}
            />
          </Form.Item>
          {kind === "BASE" && (
            <Form.Item name="baseTableId" label="注册表 ID" rules={[{ required: true, message: "请输入注册表 ID" }]}>
              <Input type="number" placeholder="已注册表的数字 ID" />
            </Form.Item>
          )}
          {kind !== "BASE" && (
            <Form.Item name="inputs" label="输入数据集 UID（逗号分隔）" rules={[{ required: true, message: "请输入输入数据集 UID" }]}>
              <Input placeholder="uid1, uid2" />
            </Form.Item>
          )}
          {kind === "DERIVED" && (
            <Form.Item name="projections" label="投影（每行一个表达式）">
              <Input.TextArea rows={3} placeholder={"t0.id\nt0.name AS customer_name"} />
            </Form.Item>
          )}
          {kind === "AGGREGATE" && (
            <>
              <Form.Item name="groupBy" label="分组维度（每行一个表达式）">
                <Input.TextArea rows={2} placeholder={"t0.dept_name\ndate_trunc('month', t0.paid_at) AS month"} />
              </Form.Item>
              <Form.Item name="measures" label="度量（每行：名称=表达式=聚合）">
                <Input.TextArea rows={2} placeholder={"sales=sum(t0.amount)=SUM"} />
              </Form.Item>
            </>
          )}
          <Form.Item name="filters" label="过滤条件（每行一个谓词）">
            <Input.TextArea rows={2} placeholder={"t0.status = 'paid'"} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
