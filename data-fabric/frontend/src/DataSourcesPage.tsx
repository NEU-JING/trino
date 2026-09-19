import { useCallback, useEffect, useState } from "react";
import * as api from "./api";
import type { DataSourceRequest, DataSourceView } from "./api";
import { cell, input } from "./styles";

const BUSINESS_TYPES = ["OceanBase", "Greenplum"];

const emptyForm: DataSourceRequest = {
  name: "",
  businessType: "OceanBase",
  host: "",
  port: 3306,
  database: "",
  user: "",
  password: "",
};

export default function DataSourcesPage({ isOperator }: { isOperator: boolean }) {
  const [dataSources, setDataSources] = useState<DataSourceView[]>([]);
  const [form, setForm] = useState<DataSourceRequest>(emptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState("");

  const refresh = useCallback(async () => {
    try {
      setDataSources(await api.listDataSources());
      setError("");
    } catch (e) {
      setError(String(e));
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onSubmit() {
    try {
      if (editingId === null) {
        await api.createDataSource(form);
      } else {
        await api.updateDataSource(editingId, form);
      }
      setForm(emptyForm);
      setEditingId(null);
      await refresh();
    } catch (e) {
      setError(String(e));
    }
  }

  async function onDisable(id: number) {
    try {
      await api.disableDataSource(id);
      await refresh();
    } catch (e) {
      setError(String(e));
    }
  }

  function onEdit(dataSource: DataSourceView) {
    setEditingId(dataSource.id);
    setForm({
      name: dataSource.name,
      businessType: dataSource.businessType,
      host: dataSource.host,
      port: dataSource.port,
      database: dataSource.database,
      user: dataSource.user,
      password: "",
    });
  }

  return (
    <section>
      {error && <p style={{ color: "crimson" }}>{error}</p>}
      <h2>数据源</h2>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr>
            <th style={cell}>名称</th>
            <th style={cell}>类型</th>
            <th style={cell}>地址</th>
            <th style={cell}>用户</th>
            <th style={cell}>状态</th>
            <th style={cell}>操作</th>
          </tr>
        </thead>
        <tbody>
          {dataSources.map((dataSource) => (
            <tr key={dataSource.id}>
              <td style={cell}>{dataSource.name}</td>
              <td style={cell}>{dataSource.businessType}</td>
              <td style={cell}>
                {dataSource.host}:{dataSource.port}
                {dataSource.database ? `/${dataSource.database}` : ""}
              </td>
              <td style={cell}>{dataSource.user}</td>
              <td style={cell}>{dataSource.enabled ? "已启用" : "已停用"}</td>
              <td style={cell}>
                {isOperator && dataSource.enabled && (
                  <>
                    <button onClick={() => onEdit(dataSource)}>编辑</button>
                    <button onClick={() => onDisable(dataSource.id)} style={{ marginLeft: 8 }}>
                      停用
                    </button>
                  </>
                )}
              </td>
            </tr>
          ))}
          {dataSources.length === 0 && (
            <tr>
              <td style={cell} colSpan={6}>
                暂无数据源
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {isOperator && (
        <>
          <h2 style={{ marginTop: 24 }}>{editingId === null ? "新增数据源" : "编辑数据源"}</h2>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 8, maxWidth: 720 }}>
            <input
              style={input}
              placeholder="名称 (小写标识)"
              value={form.name}
              disabled={editingId !== null}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
            <select
              style={input}
              value={form.businessType}
              onChange={(e) => setForm({ ...form, businessType: e.target.value })}
            >
              {BUSINESS_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
            <input style={input} placeholder="主机" value={form.host} onChange={(e) => setForm({ ...form, host: e.target.value })} />
            <input
              style={input}
              type="number"
              placeholder="端口"
              value={form.port}
              onChange={(e) => setForm({ ...form, port: Number(e.target.value) })}
            />
            <input
              style={input}
              placeholder="数据库 (Greenplum 必填)"
              value={form.database}
              onChange={(e) => setForm({ ...form, database: e.target.value })}
            />
            <input style={input} placeholder="用户名" value={form.user} onChange={(e) => setForm({ ...form, user: e.target.value })} />
            <input
              style={input}
              type="password"
              placeholder="密码"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
            />
          </div>
          <div style={{ marginTop: 8 }}>
            <button onClick={onSubmit}>{editingId === null ? "注册" : "保存"}</button>
            {editingId !== null && (
              <button
                style={{ marginLeft: 8 }}
                onClick={() => {
                  setEditingId(null);
                  setForm(emptyForm);
                }}
              >
                取消
              </button>
            )}
          </div>
        </>
      )}
    </section>
  );
}
