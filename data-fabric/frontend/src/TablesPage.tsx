import { App as AntdApp, Button, Card, Empty, Input, Popconfirm, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState, type Key } from "react";
import * as api from "./api";
import type { DataSourceView, DiscoveredTable, RegisteredTableView } from "./api";
import TableDetailDrawer from "./components/TableDetailDrawer";

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
  const [schemaFilter, setSchemaFilter] = useState<string | undefined>(undefined);
  const [selectedKeys, setSelectedKeys] = useState<Key[]>([]);
  const [descriptionTemplate, setDescriptionTemplate] = useState("{table} 业务表");

  const [detailTableId, setDetailTableId] = useState<number | null>(null);

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

  const selectedSourceName = useMemo(
    () => dataSources.find((source) => source.id === selectedSource)?.name,
    [dataSources, selectedSource],
  );

  const registeredKeys = useMemo(
    () => new Set(tables.map((table) => `${table.catalog}.${table.schema}.${table.table}`)),
    [tables],
  );

  const schemas = useMemo(
    () => Array.from(new Set(discovered.map((table) => table.schema))).sort(),
    [discovered],
  );

  const visibleDiscovered = useMemo(
    () => discovered.filter((table) => !schemaFilter || table.schema === schemaFilter),
    [discovered, schemaFilter],
  );

  function isRegistered(table: DiscoveredTable): boolean {
    return selectedSourceName !== undefined && registeredKeys.has(`${selectedSourceName}.${table.schema}.${table.table}`);
  }

  function renderTemplate(table: DiscoveredTable): string {
    return descriptionTemplate
      .replaceAll("{schema}", table.schema)
      .replaceAll("{table}", table.table)
      .replaceAll("{type}", table.type);
  }

  async function onDiscover() {
    if (selectedSource === undefined) {
      message.warning("请先选择数据源");
      return;
    }
    setDiscovering(true);
    try {
      setDiscovered(await api.discoverTables(selectedSource));
      setDescriptions({});
      setSchemaFilter(undefined);
      setSelectedKeys([]);
    } catch (e) {
      message.error("发现表失败：" + String(e));
    } finally {
      setDiscovering(false);
    }
  }

  async function register(table: DiscoveredTable): Promise<void> {
    if (selectedSource === undefined) {
      return;
    }
    const key = table.schema + "." + table.table;
    const description = descriptions[key] ?? renderTemplate(table);
    await api.registerTable({
      dataSourceId: selectedSource,
      schema: table.schema,
      table: table.table,
      description,
    });
  }

  async function onRegister(table: DiscoveredTable) {
    try {
      await register(table);
      message.success("表已注册");
      await search(query);
    } catch (e) {
      message.error(String(e));
    }
  }

  async function onBatchRegister() {
    const targets = visibleDiscovered.filter(
      (table) => selectedKeys.includes(table.schema + "." + table.table) && !isRegistered(table),
    );
    if (targets.length === 0) {
      message.warning("请先勾选未注册的表");
      return;
    }
    try {
      for (const table of targets) {
        await register(table);
      }
      message.success(`已注册 ${targets.length} 张表`);
      setSelectedKeys([]);
      await search(query);
    } catch (e) {
      message.error(String(e));
    }
  }

  function onApplyTemplate() {
    const next = { ...descriptions };
    for (const table of visibleDiscovered) {
      const key = table.schema + "." + table.table;
      if (!next[key]) {
        next[key] = renderTemplate(table);
      }
    }
    setDescriptions(next);
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
    {
      title: "操作",
      width: 160,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => setDetailTableId(record.id)}>
            详情
          </Button>
          {isOperator && (
            <Popconfirm title="确认注销该表？" onConfirm={() => onUnregister(record.id)} okText="注销" cancelText="取消">
              <Button type="link" size="small" danger>
                注销
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const discoveredColumns: ColumnsType<DiscoveredTable> = [
    { title: "Schema", dataIndex: "schema", width: 160 },
    { title: "表", dataIndex: "table" },
    { title: "类型", dataIndex: "type", width: 120 },
    {
      title: "状态",
      width: 100,
      render: (_, record) =>
        isRegistered(record) ? <Tag color="green">已注册</Tag> : <Tag>未注册</Tag>,
    },
    {
      title: "描述",
      width: 240,
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
      width: 90,
      render: (_, record) =>
        isRegistered(record) ? (
          <Tag color="green">已注册</Tag>
        ) : (
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
              style={{ width: 240 }}
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
            {discovered.length > 0 && (
              <>
                <Select
                  style={{ width: 180 }}
                  allowClear
                  placeholder="按 schema 筛选"
                  value={schemaFilter}
                  onChange={setSchemaFilter}
                  options={schemas.map((schema) => ({ value: schema, label: schema }))}
                />
                <Input
                  style={{ width: 200 }}
                  placeholder="描述模板"
                  value={descriptionTemplate}
                  onChange={(e) => setDescriptionTemplate(e.target.value)}
                />
                <Button onClick={onApplyTemplate}>应用模板</Button>
                <Button type="primary" ghost onClick={onBatchRegister}>
                  批量注册（{selectedKeys.length}）
                </Button>
              </>
            )}
          </Space>
          <Table<DiscoveredTable>
            rowKey={(record) => record.schema + "." + record.table}
            columns={discoveredColumns}
            dataSource={visibleDiscovered}
            rowSelection={{
              selectedRowKeys: selectedKeys,
              onChange: setSelectedKeys,
              getCheckboxProps: (record) => ({ disabled: isRegistered(record) }),
            }}
            pagination={{ pageSize: 8, hideOnSinglePage: true }}
            locale={{ emptyText: <Empty description="选择数据源后点击“发现表”" /> }}
          />
        </Card>
      )}

      <TableDetailDrawer tableId={detailTableId} open={detailTableId !== null} onClose={() => setDetailTableId(null)} />
    </div>
  );
}
