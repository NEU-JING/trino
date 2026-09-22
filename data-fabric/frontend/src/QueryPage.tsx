import {
  DownloadOutlined,
  FormatPainterOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  StopOutlined,
} from "@ant-design/icons";
import { Alert, App as AntdApp, Button, Card, Space, Splitter, Tabs, Tag, Tooltip } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";
import * as api from "./api";
import type { QueryExecutionView, RegisteredTableView } from "./api";
import HistoryPanel from "./query/HistoryPanel";
import ObjectTree from "./query/ObjectTree";
import ResultView from "./query/ResultView";
import SavedQueryPanel from "./query/SavedQueryPanel";
import { SqlEditor, type SqlEditorHandle } from "./query/SqlEditor";
import TableDetailPanel from "./query/TableDetailPanel";
import { formatSql } from "./query/sqlFormat";
import { useThemeMode } from "./theme";

const STATE_COLOR: Record<string, string> = {
  FINISHED: "green",
  RUNNING: "blue",
  FAILED: "red",
  CANCELED: "default",
};

const MAIN_SPLIT_KEY = "df-workbench-split-main";
const RIGHT_SPLIT_KEY = "df-workbench-split-right";

function readPercent(key: string, fallback: number[]): number[] {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) {
      return fallback;
    }
    const parsed: unknown = JSON.parse(raw);
    if (
      Array.isArray(parsed) &&
      parsed.length === fallback.length &&
      parsed.every((value) => typeof value === "number")
    ) {
      return parsed as number[];
    }
  } catch {
    // ignore storage access errors
  }
  return fallback;
}

function persistResize(key: string, sizes: number[]): void {
  const total = sizes.reduce((sum, size) => sum + size, 0);
  if (total <= 0) {
    return;
  }
  const percent = sizes.map((size) => Math.round((size / total) * 1000) / 10);
  try {
    localStorage.setItem(key, JSON.stringify(percent));
  } catch {
    // ignore storage access errors
  }
}

export default function QueryPage() {
  const { message } = AntdApp.useApp();
  const { mode } = useThemeMode();
  const [sql, setSql] = useState("SELECT 1");
  const [view, setView] = useState<QueryExecutionView | null>(null);
  const [running, setRunning] = useState(false);
  const [selectedTable, setSelectedTable] = useState<RegisteredTableView | null>(null);
  const [bottomTab, setBottomTab] = useState("result");
  const [historyKey, setHistoryKey] = useState(0);
  const [treeKey, setTreeKey] = useState(0);
  const [treeVisible, setTreeVisible] = useState(() => window.innerWidth >= 900);
  const [mainSizes] = useState(() => readPercent(MAIN_SPLIT_KEY, [24, 76]));
  const [rightSizes] = useState(() => readPercent(RIGHT_SPLIT_KEY, [52, 48]));

  const pollRef = useRef<number | null>(null);
  const editorRef = useRef<SqlEditorHandle | null>(null);

  const stopPolling = useCallback(() => {
    if (pollRef.current !== null) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  useEffect(() => {
    return () => stopPolling();
  }, [stopPolling]);

  const poll = useCallback(
    (queryId: string) => {
      pollRef.current = window.setInterval(async () => {
        try {
          const current = await api.getQuery(queryId);
          setView(current);
          if (current.state !== "RUNNING") {
            stopPolling();
            setRunning(false);
            setHistoryKey((key) => key + 1);
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
    },
    [message, stopPolling],
  );

  const runSql = useCallback(
    async (text: string) => {
      if (!text.trim()) {
        message.warning("请输入 SQL");
        return;
      }
      stopPolling();
      setView(null);
      setRunning(true);
      setBottomTab("result");
      try {
        const started = await api.startQuery(text);
        setView(started);
        poll(started.queryId);
      } catch (e) {
        message.error(String(e));
        setRunning(false);
      }
    },
    [message, poll, stopPolling],
  );

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
    setHistoryKey((key) => key + 1);
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

  const onInsert = useCallback((text: string) => {
    editorRef.current?.insertText(text);
    editorRef.current?.focus();
  }, []);

  const onSelectTable = useCallback((table: RegisteredTableView) => {
    setSelectedTable(table);
    setBottomTab("detail");
  }, []);

  const onLoadSql = useCallback((text: string) => {
    setSql(text);
  }, []);

  const onRerun = useCallback(
    (text: string) => {
      setSql(text);
      void runSql(text);
    },
    [runSql],
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, height: "calc(100vh - 170px)", minHeight: 560 }}>
      <Card
        className="df-glass"
        variant="borderless"
        title="SQL 工作台"
        styles={{ body: { paddingBottom: 12 } }}
        extra={
          <Space>
            <Tooltip title={treeVisible ? "折叠对象树" : "展开对象树"}>
              <Button
                size="small"
                icon={treeVisible ? <MenuFoldOutlined /> : <MenuUnfoldOutlined />}
                onClick={() => setTreeVisible((visible) => !visible)}
              />
            </Tooltip>
            <Tooltip title="刷新对象树">
              <Button
                size="small"
                icon={<ReloadOutlined />}
                onClick={() => {
                  setTreeKey((key) => key + 1);
                  editorRef.current?.clearCompletionCache();
                }}
              />
            </Tooltip>
            {view && <Tag color={STATE_COLOR[view.state] ?? "default"}>状态：{view.state}</Tag>}
          </Space>
        }
      >
        <Space wrap>
          <Button type="primary" icon={<PlayCircleOutlined />} onClick={() => void runSql(sql)} loading={running}>
            执行
          </Button>
          <Button icon={<StopOutlined />} onClick={() => void onCancel()} disabled={!running}>
            取消
          </Button>
          <Button icon={<FormatPainterOutlined />} onClick={() => setSql(formatSql(sql))}>
            格式化
          </Button>
          {view?.state === "FINISHED" && (
            <>
              <Button icon={<DownloadOutlined />} onClick={() => void onExport("csv")}>
                导出 CSV
              </Button>
              <Button icon={<DownloadOutlined />} onClick={() => void onExport("xlsx")}>
                导出 Excel
              </Button>
            </>
          )}
          <Tooltip title="快捷键 Ctrl/Cmd + Enter">
            <Tag>Ctrl/Cmd + Enter 执行</Tag>
          </Tooltip>
        </Space>

        {view?.error && <Alert type="error" showIcon message={view.error} style={{ marginTop: 12 }} />}
        {view?.truncated && (
          <Alert type="warning" showIcon message="结果已截断，仅显示前部分行。" style={{ marginTop: 12 }} />
        )}
      </Card>

      <div style={{ flex: 1, minHeight: 0 }}>
        <Splitter onResizeEnd={(sizes) => persistResize(MAIN_SPLIT_KEY, sizes)}>
          {treeVisible && (
            <Splitter.Panel defaultSize={`${mainSizes[0]}%`} min="14%" max="45%" collapsible>
              <div className="df-glass" style={{ height: "100%", padding: 8, borderRadius: 12, overflow: "hidden" }}>
                <ObjectTree refreshKey={treeKey} onSelectTable={onSelectTable} onInsert={onInsert} />
              </div>
            </Splitter.Panel>
          )}
          <Splitter.Panel>
            <Splitter layout="vertical" onResizeEnd={(sizes) => persistResize(RIGHT_SPLIT_KEY, sizes)}>
              <Splitter.Panel defaultSize={`${rightSizes[0]}%`} min="35%">
                <div className="df-glass" style={{ height: "100%", borderRadius: 12, overflow: "hidden" }}>
                  <SqlEditor
                    ref={editorRef}
                    value={sql}
                    onChange={setSql}
                    onRun={() => void runSql(sql)}
                    mode={mode}
                  />
                </div>
              </Splitter.Panel>
              <Splitter.Panel>
                <div className="df-glass" style={{ height: "100%", padding: "0 8px 8px", borderRadius: 12, overflow: "hidden" }}>
                  <Tabs
                    size="small"
                    activeKey={bottomTab}
                    onChange={setBottomTab}
                    style={{ height: "100%" }}
                    items={[
                      {
                        key: "result",
                        label: "结果",
                        children: (
                          <div style={{ height: "calc(100% - 44px)", minHeight: 0 }}>
                            <ResultView view={view} dark={mode === "dark"} />
                          </div>
                        ),
                      },
                      {
                        key: "detail",
                        label: "表详情",
                        children: (
                          <div style={{ height: "calc(100% - 44px)", minHeight: 0 }}>
                            <TableDetailPanel table={selectedTable} onInsert={onInsert} />
                          </div>
                        ),
                      },
                      {
                        key: "history",
                        label: "历史",
                        children: (
                          <div style={{ height: "calc(100% - 44px)", minHeight: 0 }}>
                            <HistoryPanel refreshKey={historyKey} onLoad={onLoadSql} onRerun={onRerun} />
                          </div>
                        ),
                      },
                      {
                        key: "favorites",
                        label: "收藏",
                        children: (
                          <div style={{ height: "calc(100% - 44px)", minHeight: 0 }}>
                            <SavedQueryPanel currentSql={sql} onExecute={onRerun} onLoad={onLoadSql} />
                          </div>
                        ),
                      },
                    ]}
                  />
                </div>
              </Splitter.Panel>
            </Splitter>
          </Splitter.Panel>
        </Splitter>
      </div>
    </div>
  );
}
