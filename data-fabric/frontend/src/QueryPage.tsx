import { DownloadOutlined, PlayCircleOutlined, StopOutlined } from "@ant-design/icons";
import { Alert, App as AntdApp, Button, Card, Empty, Input, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useRef, useState } from "react";
import * as api from "./api";
import type { QueryExecutionView } from "./api";

const STATE_COLOR: Record<string, string> = {
  FINISHED: "green",
  RUNNING: "blue",
  FAILED: "red",
  CANCELED: "default",
};

export default function QueryPage() {
  const { message } = AntdApp.useApp();
  const [sql, setSql] = useState("SELECT 1");
  const [view, setView] = useState<QueryExecutionView | null>(null);
  const [running, setRunning] = useState(false);
  const pollRef = useRef<number | null>(null);

  useEffect(() => {
    return () => stopPolling();
  }, []);

  function stopPolling() {
    if (pollRef.current !== null) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }

  function poll(queryId: string) {
    pollRef.current = window.setInterval(async () => {
      try {
        const current = await api.getQuery(queryId);
        setView(current);
        if (current.state !== "RUNNING") {
          stopPolling();
          setRunning(false);
          if (current.state === "FAILED") {
            message.error("查询失败");
          }
        }
      } catch (e) {
        message.error(String(e));
        stopPolling();
        setRunning(false);
      }
    }, 500);
  }

  async function onRun() {
    setView(null);
    setRunning(true);
    try {
      const started = await api.startQuery(sql);
      setView(started);
      poll(started.queryId);
    } catch (e) {
      message.error(String(e));
      setRunning(false);
    }
  }

  async function onCancel() {
    if (!view) {
      return;
    }
    try {
      setView(await api.cancelQuery(view.queryId));
      message.info("查询已取消");
    } catch (e) {
      message.error(String(e));
    }
    stopPolling();
    setRunning(false);
  }

  async function onExport(format: "csv" | "xlsx") {
    if (!view) {
      return;
    }
    try {
      await api.downloadExport(view.queryId, format);
      message.success("导出已开始");
    } catch (e) {
      message.error(String(e));
    }
  }

  const columns: ColumnsType<Record<string, unknown>> = (view?.columns ?? []).map((column, index) => ({
    title: column,
    dataIndex: "c" + index,
    render: (value: unknown) => (value === null || value === undefined ? "" : String(value)),
  }));

  const dataSource = (view?.rows ?? []).map((row, rowIndex) => {
    const record: Record<string, unknown> = { key: rowIndex };
    row.forEach((value, columnIndex) => {
      record["c" + columnIndex] = value;
    });
    return record;
  });

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <Card
        className="df-glass"
        variant="borderless"
        title="SQL 查询"
        extra={view && <Tag color={STATE_COLOR[view.state] ?? "default"}>状态：{view.state}</Tag>}
      >
        <Input.TextArea
          value={sql}
          onChange={(e) => setSql(e.target.value)}
          autoSize={{ minRows: 5, maxRows: 10 }}
          style={{ fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace", fontSize: 14 }}
          placeholder="输入 SQL，例如 SELECT * FROM oceanbase.ob_source.orders"
        />
        <Space style={{ marginTop: 12 }} wrap>
          <Button type="primary" icon={<PlayCircleOutlined />} onClick={onRun} loading={running}>
            执行
          </Button>
          <Button icon={<StopOutlined />} onClick={onCancel} disabled={!running}>
            取消
          </Button>
          {view?.state === "FINISHED" && (
            <>
              <Button icon={<DownloadOutlined />} onClick={() => onExport("csv")}>
                导出 CSV
              </Button>
              <Button icon={<DownloadOutlined />} onClick={() => onExport("xlsx")}>
                导出 Excel
              </Button>
            </>
          )}
        </Space>

        {view?.error && <Alert type="error" showIcon message={view.error} style={{ marginTop: 12 }} />}
        {view?.truncated && (
          <Alert type="warning" showIcon message="结果已截断，仅显示前部分行。" style={{ marginTop: 12 }} />
        )}

        {view && (
          <div style={{ marginTop: 16 }}>
            <Table<Record<string, unknown>>
              size="small"
              rowKey="key"
              columns={columns}
              dataSource={dataSource}
              scroll={{ x: "max-content" }}
              pagination={{ pageSize: 20, hideOnSinglePage: true }}
              locale={{ emptyText: <Empty description="（无数据）" /> }}
            />
          </div>
        )}
      </Card>
    </div>
  );
}
