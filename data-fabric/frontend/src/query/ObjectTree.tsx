import { DatabaseOutlined, FolderOutlined, PlusOutlined, TableOutlined } from "@ant-design/icons";
import { App as AntdApp, Button, Empty, Input, Spin, Tooltip, Tree, Typography } from "antd";
import type { TreeDataNode, TreeProps } from "antd";
import { useCallback, useEffect, useMemo, useRef, useState, type Key, type ReactNode } from "react";
import * as api from "../api";
import type { ColumnMetadataView, RegisteredTableView } from "../api";

interface Props {
  onSelectTable: (table: RegisteredTableView) => void;
  onInsert: (text: string) => void;
  refreshKey?: number;
}

function qualifiedName(table: RegisteredTableView): string {
  return `${table.catalog}.${table.schema}.${table.table}`;
}

export default function ObjectTree({ onSelectTable, onInsert, refreshKey }: Props) {
  const { message } = AntdApp.useApp();
  const [tables, setTables] = useState<RegisteredTableView[]>([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [columnsByTable, setColumnsByTable] = useState<Record<string, ColumnMetadataView[]>>({});
  const cacheRef = useRef(new Map<string, ColumnMetadataView[]>());

  const load = useCallback(
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
    void load(query);
  }, [load, query, refreshKey]);

  const tableByQualified = useMemo(() => {
    const map = new Map<string, RegisteredTableView>();
    tables.forEach((table) => map.set(qualifiedName(table), table));
    return map;
  }, [tables]);

  const onLoadColumns = useCallback(async (node: TreeDataNode) => {
    const key = String(node.key);
    if (!key.startsWith("t:")) {
      return;
    }
    const qualified = key.slice(2);
    const table = tableByQualified.get(qualified);
    if (!table) {
      return;
    }
    const cached = cacheRef.current.get(qualified);
    if (cached) {
      setColumnsByTable((previous) => ({ ...previous, [qualified]: cached }));
      return;
    }
    try {
      const columns = await api.getTableColumns(table.id);
      cacheRef.current.set(qualified, columns);
      setColumnsByTable((previous) => ({ ...previous, [qualified]: columns }));
    } catch {
      cacheRef.current.set(qualified, []);
      setColumnsByTable((previous) => ({ ...previous, [qualified]: [] }));
    }
  }, [tableByQualified]);

  const renderInsert = (text: string, label: string): ReactNode => (
    <Tooltip title={label}>
      <Button
        type="text"
        size="small"
        icon={<PlusOutlined />}
        onClick={(event) => {
          event.stopPropagation();
          onInsert(text);
        }}
      />
    </Tooltip>
  );

  const treeData = useMemo<TreeDataNode[]>(() => {
    const byCatalog = new Map<string, Map<string, RegisteredTableView[]>>();
    for (const table of tables) {
      let schemas = byCatalog.get(table.catalog);
      if (!schemas) {
        schemas = new Map();
        byCatalog.set(table.catalog, schemas);
      }
      const list = schemas.get(table.schema) ?? [];
      list.push(table);
      schemas.set(table.schema, list);
    }
    return [...byCatalog.entries()]
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([catalog, schemas]) => ({
        key: `c:${catalog}`,
        selectable: false,
        icon: <DatabaseOutlined />,
        title: <Typography.Text strong>{catalog}</Typography.Text>,
        children: [...schemas.entries()]
          .sort(([left], [right]) => left.localeCompare(right))
          .map(([schema, list]) => ({
            key: `s:${catalog}.${schema}`,
            selectable: false,
            icon: <FolderOutlined />,
            title: schema,
            children: list
              .sort((left, right) => left.table.localeCompare(right.table))
              .map((table) => {
                const qualified = qualifiedName(table);
                const columns = columnsByTable[qualified];
                return {
                  key: `t:${qualified}`,
                  isLeaf: false,
                  icon: <TableOutlined />,
                  title: (
                    <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
                      <Tooltip title={table.description || qualified}>
                        <span>{table.table}</span>
                      </Tooltip>
                      {renderInsert(qualified, "将限定表名插入编辑器")}
                    </span>
                  ),
                  children: columns?.map((column) => ({
                    key: `col:${qualified}.${column.name}`,
                    isLeaf: true,
                    title: (
                      <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
                        <Tooltip title={column.comment || column.type}>
                          <span>
                            {column.name}
                            <Typography.Text type="secondary" style={{ marginLeft: 6, fontSize: 12 }}>
                              {column.type}
                              {column.nullable ? "" : " · NOT NULL"}
                            </Typography.Text>
                          </span>
                        </Tooltip>
                        {renderInsert(column.name, "将列名插入编辑器")}
                      </span>
                    ),
                  })),
                };
              }),
          })),
      }));
  }, [tables, columnsByTable, onInsert]);

  const onSelect: TreeProps["onSelect"] = (keys: Key[], info) => {
    const key = String(info.node.key);
    if (!key.startsWith("t:")) {
      return;
    }
    const table = tableByQualified.get(key.slice(2));
    if (table) {
      onSelectTable(table);
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", minWidth: 0 }}>
      <Input.Search
        allowClear
        placeholder="搜索表名或含义"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        onSearch={(value) => setQuery(value)}
        style={{ marginBottom: 8 }}
      />
      <div style={{ flex: 1, overflow: "auto", minHeight: 0 }}>
        {loading && tables.length === 0 ? (
          <Spin />
        ) : tables.length === 0 ? (
          <Empty description="暂无可查询的表" />
        ) : (
          <Tree
            showIcon
            blockNode
            treeData={treeData}
            loadData={onLoadColumns}
            onSelect={onSelect}
          />
        )}
      </div>
    </div>
  );
}
