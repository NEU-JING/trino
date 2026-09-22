import { ReloadOutlined, SelectOutlined } from "@ant-design/icons";
import { App as AntdApp, Button, Empty, Space, Table, Tag, Tooltip, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import * as api from "../api";
import type { QueryHistoryEntry } from "../api";

const STATE_COLOR: Record<string, string> = {
  FINISHED: "green",
  RUNNING: "blue",
  FAILED: "red",
  CANCELED: "default",
};

interface Props {
  refreshKey: number;
  onLoad: (sql: string) => void;
  onRerun: (sql: string) => void;
}

function formatDuration(entry: QueryHistoryEntry): string {
  if (!entry.finishedAt) {
    return "-";
  }
  const millis = new Date(entry.finishedAt).getTime() - new Date(entry.startedAt).getTime();
  return `${millis} ms`;
}

export default function HistoryPanel({ refreshKey, onLoad, onRerun }: Props) {
  const { message } = AntdApp.useApp();
  const [entries, setEntries] = useState<QueryHistoryEntry[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setEntries(await api.listQueryHistory());
    } catch (e) {
      message.error("加载查询历史失败：" + String(e));
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  const columns: ColumnsType<QueryHistoryEntry> = [
    {
      title: "SQL",
      dataIndex: "sql",
      ellipsis: true,
      render: (sql: string) => (
        <Tooltip title={<pre className="df-cell-full">{sql}</pre>} placement="topLeft">
          <Typography.Text code>{sql}</Typography.Text>
        </Tooltip>
      ),
    },
    {
      title: "状态",
      dataIndex: "state",
      width: 100,
      render: (state: string) => (
        <Tag color={STATE_COLOR[state] ?? "default"}>{state}</Tag>
      ),
    },
    {
      title: "行数",
      dataIndex: "rowCount",
      width: 80,
      render: (rowCount: number | null) => (rowCount === null ? "-" : rowCount),
    },
    {
      title: "耗时",
      width: 100,
      render: (_, entry) => formatDuration(entry),
    },
    {
      title: "时间",
      dataIndex: "startedAt",
      width: 170,
      render: (value: string) => (value ? new Date(value).toLocaleString() : "-"),
    },
    {
      title: "操作",
      width: 140,
      render: (_, entry) => (
        <Space>
          <Button type="link" size="small" icon={<SelectOutlined />} onClick={() => onLoad(entry.sql)}>
            回看
          </Button>
          <Button type="link" size="small" icon={<ReloadOutlined />} onClick={() => onRerun(entry.sql)}>
            重跑
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ height: "100%", overflow: "auto" }}>
      <Table<QueryHistoryEntry>
        size="small"
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={entries}
        pagination={{ pageSize: 20, size: "small", showTotal: (total) => `共 ${total} 条` }}
        locale={{ emptyText: <Empty description="暂无查询历史" /> }}
      />
    </div>
  );
}
