import { useCallback, useEffect, useState } from "react";
import * as api from "./api";
import type { DataSourceView, DiscoveredTable, RegisteredTableView } from "./api";
import { cell, input } from "./styles";

export default function TablesPage({ isOperator }: { isOperator: boolean }) {
  const [query, setQuery] = useState("");
  const [tables, setTables] = useState<RegisteredTableView[]>([]);
  const [error, setError] = useState("");

  const [dataSources, setDataSources] = useState<DataSourceView[]>([]);
  const [selectedSource, setSelectedSource] = useState<string>("");
  const [discovered, setDiscovered] = useState<DiscoveredTable[]>([]);
  const [descriptions, setDescriptions] = useState<Record<string, string>>({});

  const search = useCallback(async (keyword: string) => {
    try {
      setTables(await api.listTables(keyword));
      setError("");
    } catch (e) {
      setError(String(e));
    }
  }, []);

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
    if (selectedSource === "") {
      return;
    }
    try {
      setDiscovered(await api.discoverTables(Number(selectedSource)));
      setError("");
    } catch (e) {
      setError(String(e));
    }
  }

  async function onRegister(table: DiscoveredTable) {
    try {
      const key = table.schema + "." + table.table;
      await api.registerTable({
        dataSourceId: Number(selectedSource),
        schema: table.schema,
        table: table.table,
        description: descriptions[key] ?? "",
      });
      await search(query);
      setDiscovered([]);
      setError("");
    } catch (e) {
      setError(String(e));
    }
  }

  async function onUnregister(id: number) {
    try {
      await api.unregisterTable(id);
      await search(query);
    } catch (e) {
      setError(String(e));
    }
  }

  return (
    <section>
      {error && <p style={{ color: "crimson" }}>{error}</p>}

      <h2>表目录</h2>
      <div style={{ marginBottom: 8 }}>
        <input
          style={input}
          placeholder="按表名或描述搜索"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button style={{ marginLeft: 8 }} onClick={() => search(query)}>
          搜索
        </button>
      </div>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr>
            <th style={cell}>数据源类型</th>
            <th style={cell}>表</th>
            <th style={cell}>描述</th>
            <th style={cell}>注册人</th>
            {isOperator && <th style={cell}>操作</th>}
          </tr>
        </thead>
        <tbody>
          {tables.map((table) => (
            <tr key={table.id}>
              <td style={cell}>{table.businessType}</td>
              <td style={cell}>
                {table.catalog}.{table.schema}.{table.table}
              </td>
              <td style={cell}>{table.description}</td>
              <td style={cell}>{table.registeredBy}</td>
              {isOperator && (
                <td style={cell}>
                  <button onClick={() => onUnregister(table.id)}>注销</button>
                </td>
              )}
            </tr>
          ))}
          {tables.length === 0 && (
            <tr>
              <td style={cell} colSpan={isOperator ? 5 : 4}>
                暂无可查询的表
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {isOperator && (
        <>
          <h2 style={{ marginTop: 24 }}>发现并注册表</h2>
          <div>
            <select style={input} value={selectedSource} onChange={(e) => setSelectedSource(e.target.value)}>
              <option value="">选择数据源</option>
              {dataSources.map((source) => (
                <option key={source.id} value={String(source.id)}>
                  {source.name}（{source.businessType}）
                </option>
              ))}
            </select>
            <button style={{ marginLeft: 8 }} onClick={onDiscover}>
              发现表
            </button>
          </div>
          {discovered.length > 0 && (
            <table style={{ width: "100%", borderCollapse: "collapse", marginTop: 8 }}>
              <thead>
                <tr>
                  <th style={cell}>Schema</th>
                  <th style={cell}>表</th>
                  <th style={cell}>类型</th>
                  <th style={cell}>描述</th>
                  <th style={cell}>操作</th>
                </tr>
              </thead>
              <tbody>
                {discovered.map((table) => {
                  const key = table.schema + "." + table.table;
                  return (
                    <tr key={key}>
                      <td style={cell}>{table.schema}</td>
                      <td style={cell}>{table.table}</td>
                      <td style={cell}>{table.type}</td>
                      <td style={cell}>
                        <input
                          style={input}
                          value={descriptions[key] ?? ""}
                          onChange={(e) => setDescriptions({ ...descriptions, [key]: e.target.value })}
                        />
                      </td>
                      <td style={cell}>
                        <button onClick={() => onRegister(table)}>注册</button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </>
      )}
    </section>
  );
}
