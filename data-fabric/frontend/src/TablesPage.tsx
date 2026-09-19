import { App as AntdApp, Button, Card, Empty, Input, Popconfirm, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import * as api from "./api";
import type { DataSourceView, DiscoveredTable, RegisteredTableView } from "./api";

export default function TablesPage({ isOperator }: { isOperator: boolean }) {
  const { message } = AntdApp.useApp();
  const [query, setQuery] = useState("");
  const [tables, setTables] = useState<RegisteredTableView[]>([]);
  const [loading, setLoading] = useState(false);

  const [dataSources, setDataSources] = useState<DataSourceView[]>([]);
  const [selectedSource, setSelectedSource] = useState<number | undefined>(undefined);
  const [discovered, setDiscovered] = useState<DiscoveredTable[]>([]);
  const [descriptions, setDescriptions] = useState<Record<string, string>>({});
  const [discovering, setDiscovering] = useState(false);

  const search = useCallback(
    async (keyword: string) => {
      setLoading(true);
      try {
        setTables(await api.listTables(keyword));
      } catch (e) {
        message.error("加载表目录失败：" + String(e));
      } finally {
        setLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    void search("");
  }, [search]);

  useEffect(() => {
    if (isOperator) {
      api
        .listDataSources()
        .then((sources) => setDataSources(sources.filter((source) => source.enabled)))
        .catch(() => undefined);
    }
  }, [isOperator]);

  async function onDiscover() {
    if (selectedSource === undefined) {
      message.warning("请先选择数据源");
      return;
    }
    setDiscovering(true);
    try {
      setDiscovered(await api.discoverTables(selectedSource));
      setDescriptions({});
    } catch (e) {
      message.error("发现表失败：" + String(e));
    } finally {
      setDiscovering(false);
    }
  }

  async function onRegister(table: DiscoveredTable) {
    if (selectedSource === undefined) {
      return;
    }
    const key = table.schema + "." + table.table;
    try {
      await api.registerTable({
        dataSourceId: selectedSource,
        schema: table.schema,
        table: table.table,
        description: descriptions[key] ?? "",
      });
      message.success("表已注册");
      await search(query);
      setDiscovered([]);
    } catch (e) {
      message.error(String(e));
    }
  }

  async function onUnregister(id: number) {
    try {
      await api.unregisterTable(id);
      message.success("表已注销");
      await search(query);
    } catch (e) {
      message.error(String(e));
    }
  }

  const columns: ColumnsType<RegisteredTableView> = [
    { title: "数据源类型", dataIndex: "businessType", width: 140, render: (value: string) => <Tag color="blue">{value}</Tag> },
    {
      title: "表",
      render: (_, record) => <Tag>{`${record.catalog}.${record.schema}.${record.table}`}</Tag>,
    },
    { title: "描述", dataIndex: "description", render: (value: string) => value || "-" },
    { title: "注册人", dataIndex: "registeredBy", width: 120 },
    ...(isOperator
      ? [
          {
            title: "操作",
            width: 100,
            render: (_: unknown, record: RegisteredTableView) => (
              <Popconfirm title="确认注销该表？" onConfirm={() => onUnregister(record.id)} okText="注销" cancelText="取消">
                <Button type="link" size="small" danger>
                  注销
                </Button>
              </Popconfirm>
            ),
          },
        ]
      : []),
  ];

  const discoveredColumns: ColumnsType<DiscoveredTable> = [
    { title: "Schema", dataIndex: "schema", width: 180 },
    { title: "表", dataIndex: "table" },
    { title: "类型", dataIndex: "type", width: 140 },
    {
      title: "描述",
      width: 260,
      render: (_, record) => {
        const key = record.schema + "." + record.table;
        return (
          <Input
            placeholder="可选描述"
            value={descriptions[key] ?? ""}
            onChange={(e) => setDescriptions({ ...descriptions, [key]: e.target.value })}
          />
        );
      },
    },
    {
      title: "操作",
      width: 100,
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => onRegister(record)}>
          注册
        </Button>
      ),
    },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <Card
        className="df-glass"
        variant="borderless"
        title="表目录"
        extra={
          <Input.Search
            allowClear
            placeholder="按表名或描述搜索"
            style={{ width: 280 }}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onSearch={(value) => {
              setQuery(value);
              void search(value);
            }}
          />
        }
      >
        <Table<RegisteredTableView>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={tables}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          locale={{ emptyText: <Empty description="暂无可查询的表" /> }}
        />
      </Card>

      {isOperator && (
        <Card className="df-glass" variant="borderless" title="发现并注册表">
          <Space style={{ marginBottom: 16 }} wrap>
            <Select
              style={{ width: 280 }}
              placeholder="选择数据源"
              value={selectedSource}
              onChange={setSelectedSource}
              options={dataSources.map((source) => ({
                value: source.id,
                label: `${source.name}（${source.businessType}）`,
              }))}
            />
            <Button type="primary" loading={discovering} onClick={onDiscover}>
              发现表
            </Button>
          </Space>
          <Table<DiscoveredTable>
            rowKey={(record) => record.schema + "." + record.table}
            columns={discoveredColumns}
            dataSource={discovered}
            pagination={{ pageSize: 8, hideOnSinglePage: true }}
            locale={{ emptyText: <Empty description="选择数据源后点击“发现表”" /> }}
          />
        </Card>
      )}
    </div>
  );
}
