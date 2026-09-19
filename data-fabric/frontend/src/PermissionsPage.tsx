import { useCallback, useEffect, useState } from "react";
import * as api from "./api";
import type { PermissionView, UserView } from "./api";
import { cell, input } from "./styles";

const empty: PermissionView = {
  principal: "",
  principalType: "USER",
  catalog: "",
  schema: "",
  table: "",
  permission: "SELECT",
};

export default function PermissionsPage() {
  const [permissions, setPermissions] = useState<PermissionView[]>([]);
  const [users, setUsers] = useState<UserView[]>([]);
  const [form, setForm] = useState<PermissionView>(empty);
  const [error, setError] = useState("");

  const refresh = useCallback(async () => {
    try {
      setPermissions(await api.listPermissions());
      setError("");
    } catch (e) {
      setError(String(e));
    }
  }, []);

  useEffect(() => {
    void refresh();
    api
      .listUsers()
      .then((result) => setUsers(result.filter((user) => user.enabled)))
      .catch(() => undefined);
  }, [refresh]);

  async function onGrant() {
    try {
      await api.grantPermission(form);
      setForm(empty);
      await refresh();
    } catch (e) {
      setError(String(e));
    }
  }

  async function onRevoke(permission: PermissionView) {
    try {
      await api.revokePermission(permission);
      await refresh();
    } catch (e) {
      setError(String(e));
    }
  }

  return (
    <section>
      {error && <p style={{ color: "crimson" }}>{error}</p>}
      <h2>权限管理</h2>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr>
            <th style={cell}>主体</th>
            <th style={cell}>类型</th>
            <th style={cell}>表</th>
            <th style={cell}>权限</th>
            <th style={cell}>操作</th>
          </tr>
        </thead>
        <tbody>
          {permissions.map((permission, index) => (
            <tr key={`${permission.principal}-${permission.catalog}-${permission.schema}-${permission.table}-${index}`}>
              <td style={cell}>{permission.principal}</td>
              <td style={cell}>{permission.principalType}</td>
              <td style={cell}>
                {permission.catalog}.{permission.schema}.{permission.table}
              </td>
              <td style={cell}>{permission.permission}</td>
              <td style={cell}>
                <button onClick={() => onRevoke(permission)}>回收</button>
              </td>
            </tr>
          ))}
          {permissions.length === 0 && (
            <tr>
              <td style={cell} colSpan={5}>
                暂无授权
              </td>
            </tr>
          )}
        </tbody>
      </table>

      <h2 style={{ marginTop: 24 }}>授予表权限</h2>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 8, maxWidth: 720 }}>
        <select
          style={input}
          value={form.principal}
          onChange={(e) => setForm({ ...form, principal: e.target.value })}
        >
          <option value="">选择用户</option>
          {users.map((user) => (
            <option key={user.username} value={user.username}>
              {user.username}（{user.role}）
            </option>
          ))}
        </select>
        <select
          style={input}
          value={form.principalType}
          onChange={(e) => setForm({ ...form, principalType: e.target.value })}
        >
          <option value="USER">USER</option>
          <option value="ROLE">ROLE</option>
        </select>
        <input style={input} placeholder="catalog" value={form.catalog} onChange={(e) => setForm({ ...form, catalog: e.target.value })} />
        <input style={input} placeholder="schema" value={form.schema} onChange={(e) => setForm({ ...form, schema: e.target.value })} />
        <input style={input} placeholder="table" value={form.table} onChange={(e) => setForm({ ...form, table: e.target.value })} />
        <select
          style={input}
          value={form.permission}
          onChange={(e) => setForm({ ...form, permission: e.target.value })}
        >
          <option value="SELECT">SELECT</option>
        </select>
      </div>
      <div style={{ marginTop: 8 }}>
        <button onClick={onGrant}>授权</button>
      </div>
    </section>
  );
}
