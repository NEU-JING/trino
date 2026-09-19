import { useEffect, useRef, useState } from "react";
import * as api from "./api";
import type { QueryExecutionView } from "./api";
import { cell } from "./styles";

export default function QueryPage() {
  const [sql, setSql] = useState("SELECT 1");
  const [view, setView] = useState<QueryExecutionView | null>(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState("");
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
        }
      } catch (e) {
        setError(String(e));
        stopPolling();
        setRunning(false);
      }
    }, 500);
  }

  async function onRun() {
    setError("");
    setView(null);
    setRunning(true);
    try {
      const started = await api.startQuery(sql);
      setView(started);
      poll(started.queryId);
    } catch (e) {
      setError(String(e));
      setRunning(false);
    }
  }

  async function onCancel() {
    if (!view) {
      return;
    }
    try {
      setView(await api.cancelQuery(view.queryId));
    } catch (e) {
      setError(String(e));
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
      setError("");
    } catch (e) {
      setError(String(e));
    }
  }

  return (
    <section>
      {error && <p style={{ color: "crimson" }}>{error}</p>}
      <h2>SQL 查询</h2>
      <textarea
        style={{ width: "100%", height: 140, fontFamily: "monospace", fontSize: 14, padding: 8 }}
        value={sql}
        onChange={(e) => setSql(e.target.value)}
      />
      <div style={{ marginTop: 8 }}>
        <button onClick={onRun} disabled={running}>
          执行
        </button>
        <button onClick={onCancel} disabled={!running} style={{ marginLeft: 8 }}>
          取消
        </button>
        {view && <span style={{ marginLeft: 12 }}>状态：{view.state}</span>}
        {view?.state === "FINISHED" && (
          <>
            <button style={{ marginLeft: 12 }} onClick={() => onExport("csv")}>
              导出 CSV
            </button>
            <button style={{ marginLeft: 8 }} onClick={() => onExport("xlsx")}>
              导出 Excel
            </button>
          </>
        )}
      </div>

      {view?.error && <p style={{ color: "crimson" }}>{view.error}</p>}
      {view?.truncated && <p style={{ color: "#b8860b" }}>结果已截断，仅显示前部分行。</p>}

      {view && view.columns.length > 0 && (
        <div style={{ overflowX: "auto", marginTop: 12 }}>
          <table style={{ borderCollapse: "collapse", width: "100%" }}>
            <thead>
              <tr>
                {view.columns.map((column) => (
                  <th key={column} style={{ ...cell, background: "#f5f5f5" }}>
                    {column}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {view.rows.map((row, rowIndex) => (
                <tr key={rowIndex}>
                  {row.map((value, columnIndex) => (
                    <td key={columnIndex} style={cell}>
                      {value === null ? "" : String(value)}
                    </td>
                  ))}
                </tr>
              ))}
              {view.rows.length === 0 && (
                <tr>
                  <td style={cell} colSpan={view.columns.length}>
                    （无数据）
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
