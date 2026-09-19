import {
  ApiOutlined,
  DatabaseOutlined,
  FieldTimeOutlined,
  TableOutlined,
  TeamOutlined,
} from "@ant-design/icons";
import { Alert, Badge, Card, Col, Empty, List, Row, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import ReactECharts from "echarts-for-react";
import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import * as api from "./api";
import type { QueryHistoryView, OverviewStatsView, TopologyView } from "./api";
import { useThemeMode } from "./theme";

const CATEGORY_INDEX: Record<string, number> = { DATA_SOURCE: 0, TABLE: 1, USER: 2, ROLE: 3 };
const CATEGORY_COLORS = ["#1668dc", "#13c2c2", "#722ed1", "#fa8c16"];
const NODE_SIZE: Record<string, number> = { DATA_SOURCE: 56, TABLE: 42, USER: 36, ROLE: 30 };

const STATE_COLOR: Record<string, string> = {
  FINISHED: "green",
  RUNNING: "blue",
  FAILED: "red",
  CANCELED: "default",
};

export default function DashboardPage({ isOperator }: { isOperator: boolean }) {
  const navigate = useNavigate();
  const { mode } = useThemeMode();
  const [stats, setStats] = useState<OverviewStatsView | null>(null);
  const [topology, setTopology] = useState<TopologyView | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const [statsResult, topologyResult] = await Promise.all([api.getOverviewStats(), api.getTopology()]);
      setStats(statsResult);
      setTopology(topologyResult);
      setError("");
    } catch (e) {
      setError(String(e));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const nodeTypes = useMemo(() => {
    const map = new Map<string, string>();
    topology?.nodes.forEach((node) => map.set(node.id, node.type));
    return map;
  }, [topology]);

  const chartOption = useMemo(() => {
    const labelColor = mode === "dark" ? "#e6f0ff" : "#0b1f3a";
    const lineColor = mode === "dark" ? "rgba(96,165,250,0.35)" : "rgba(22,104,220,0.25)";
    const nodes = (topology?.nodes ?? []).map((node) => ({
      id: node.id,
      name: node.label,
      category: CATEGORY_INDEX[node.type] ?? 2,
      symbolSize: NODE_SIZE[node.type] ?? 34,
      itemStyle: {
        color: CATEGORY_COLORS[CATEGORY_INDEX[node.type] ?? 2],
        opacity: node.enabled === false ? 0.35 : 1,
        borderColor: labelColor,
        borderWidth: node.enabled === false ? 0 : 1,
      },
    }));
    const edges = (topology?.edges ?? []).map((edge) => ({
      source: edge.source,
      target: edge.target,
      lineStyle: { type: edge.type === "GRANT" ? "dashed" : "solid", color: lineColor },
    }));
    return {
      tooltip: { trigger: "item" },
      legend: [
        {
          data: ["数据源", "注册表", "用户", "角色"],
          textStyle: { color: labelColor },
          bottom: 0,
        },
      ],
      series: [
        {
          type: "graph",
          layout: "force",
          roam: true,
          draggable: true,
          force: { repulsion: 260, edgeLength: 100, gravity: 0.08 },
          categories: [{ name: "数据源" }, { name: "注册表" }, { name: "用户" }, { name: "角色" }],
          label: { show: true, color: labelColor, fontSize: 12 },
          lineStyle: { color: lineColor, curveness: 0.08, width: 1.4 },
          emphasis: { focus: "adjacency", lineStyle: { width: 2.4 } },
          data: nodes,
          edges,
        },
      ],
    };
  }, [topology, mode]);

  function onNodeClick(params: { dataType?: string; data?: { id?: string } }) {
    if (params.dataType !== "node" || !params.data?.id) {
      return;
    }
    const type = nodeTypes.get(params.data.id);
    if (type === "DATA_SOURCE") {
      navigate(isOperator ? "/data-sources" : "/tables");
    } else if (type === "TABLE") {
      navigate("/tables");
    }
  }

  const queryColumns: ColumnsType<QueryHistoryView> = [
    { title: "用户", dataIndex: "user", width: 110 },
    {
      title: "SQL",
      dataIndex: "sql",
      ellipsis: true,
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: "状态",
      dataIndex: "state",
      width: 110,
      render: (value: string) => <Tag color={STATE_COLOR[value] ?? "default"}>{value}</Tag>,
    },
    {
      title: "开始时间",
      dataIndex: "startedAt",
      width: 200,
      render: (value: string) => (value ? new Date(value).toLocaleString() : "-"),
    },
  ];

  interface Kpi {
    label: string;
    value: number;
    suffix?: string;
    icon: ReactNode;
  }

  const kpis: Kpi[] = isOperator
    ? [
        {
          label: "数据源",
          value: stats?.enabledDataSourceCount ?? 0,
          suffix: `/ ${stats?.dataSourceCount ?? 0}`,
          icon: <DatabaseOutlined style={{ color: CATEGORY_COLORS[0] }} />,
        },
        {
          label: "注册表",
          value: stats?.registeredTableCount ?? 0,
          icon: <TableOutlined style={{ color: CATEGORY_COLORS[1] }} />,
        },
        {
          label: "用户",
          value: stats?.userCount ?? 0,
          icon: <TeamOutlined style={{ color: CATEGORY_COLORS[2] }} />,
        },
        {
          label: "查询量",
          value: stats?.queryCount ?? 0,
          icon: <FieldTimeOutlined style={{ color: CATEGORY_COLORS[3] }} />,
        },
      ]
    : [
        {
          label: "可查询表",
          value: stats?.registeredTableCount ?? 0,
          icon: <TableOutlined style={{ color: CATEGORY_COLORS[1] }} />,
        },
        {
          label: "我的查询",
          value: stats?.queryCount ?? 0,
          icon: <FieldTimeOutlined style={{ color: CATEGORY_COLORS[3] }} />,
        },
      ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        平台总览
      </Typography.Title>

      {error && <Alert type="error" showIcon message="加载总览数据失败" description={error} />}

      <Row gutter={16}>
        {kpis.map((kpi) => (
          <Col xs={12} md={6} key={kpi.label}>
            <Card className="df-glass" variant="borderless">
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <div className="df-kpi-label">{kpi.label}</div>
                  <div className="df-kpi-value">
                    {kpi.value}
                    {kpi.suffix && (
                      <Typography.Text type="secondary" style={{ fontSize: 14, marginLeft: 6 }}>
                        {kpi.suffix}
                      </Typography.Text>
                    )}
                  </div>
                </div>
                <span style={{ fontSize: 26 }}>{kpi.icon}</span>
              </div>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={16}>
        {isOperator && (
          <Col xs={24} lg={10}>
            <Card
              className="df-glass"
              variant="borderless"
              title={
                <span>
                  <ApiOutlined /> 数据源健康
                </span>
              }
            >
            {stats && stats.dataSourceHealth.length > 0 ? (
              <List
                dataSource={stats.dataSourceHealth}
                renderItem={(item) => (
                  <List.Item
                    actions={[
                      <Tag color={item.enabled ? "green" : "default"} key="status">
                        {item.enabled ? "已启用" : "已停用"}
                      </Tag>,
                    ]}
                  >
                    <List.Item.Meta
                      avatar={
                        <Badge
                          status={item.enabled ? "success" : "default"}
                          text={item.name}
                          style={{ whiteSpace: "nowrap" }}
                        />
                      }
                      description={item.businessType}
                    />
                  </List.Item>
                )}
              />
              ) : (
                <Empty description="暂无数据源" />
              )}
            </Card>
          </Col>
        )}
        <Col xs={24} lg={isOperator ? 14 : 24}>
          <Card
            className="df-glass"
            variant="borderless"
            title={
              <span>
                <FieldTimeOutlined /> {isOperator ? "最近查询" : "我的最近查询"}
              </span>
            }
          >
            <Table<QueryHistoryView>
              size="small"
              rowKey="queryId"
              pagination={false}
              columns={queryColumns}
              dataSource={stats?.recentQueries ?? []}
              locale={{ emptyText: <Empty description="暂无查询记录" /> }}
            />
          </Card>
        </Col>
      </Row>

      <Card
        className="df-glass"
        variant="borderless"
        title={<span>数据编织拓扑</span>}
      >
        {topology && topology.nodes.length > 0 ? (
          <ReactECharts
            option={chartOption}
            style={{ height: 460 }}
            onEvents={{ click: onNodeClick }}
            notMerge
          />
        ) : (
          <Empty description="暂无拓扑数据" />
        )}
      </Card>
    </div>
  );
}
