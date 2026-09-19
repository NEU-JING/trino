export interface DataSourceView {
  id: number;
  name: string;
  businessType: string;
  host: string;
  port: number;
  database: string;
  user: string;
  enabled: boolean;
  createdBy: string;
  createdAt: string;
}

export interface DataSourceRequest {
  name: string;
  businessType: string;
  host: string;
  port: number;
  database: string;
  user: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  role: string;
}

export interface DiscoveredTable {
  schema: string;
  table: string;
  type: string;
}

export interface RegisteredTableView {
  id: number;
  catalog: string;
  schema: string;
  table: string;
  description: string;
  businessType: string;
  registeredBy: string;
  createdAt: string;
}

export interface RegisterTableRequest {
  dataSourceId: number;
  schema: string;
  table: string;
  description: string;
}

export interface PermissionView {
  principal: string;
  principalType: string;
  catalog: string;
  schema: string;
  table: string;
  permission: string;
}

export interface UserView {
  username: string;
  role: string;
  enabled: boolean;
}

export interface QueryExecutionView {
  queryId: string;
  state: string;
  columns: string[];
  rows: unknown[][];
  truncated: boolean;
  error: string | null;
  startedAt: string;
  finishedAt: string | null;
}

export interface DataSourceHealthView {
  name: string;
  businessType: string;
  enabled: boolean;
}

export interface QueryHistoryView {
  queryId: string;
  user: string;
  sql: string;
  state: string;
  startedAt: string;
  finishedAt: string | null;
}

export interface OverviewStatsView {
  dataSourceCount: number;
  enabledDataSourceCount: number;
  registeredTableCount: number;
  userCount: number;
  queryCount: number;
  dataSourceHealth: DataSourceHealthView[];
  recentQueries: QueryHistoryView[];
}

export interface TopologyNode {
  id: string;
  label: string;
  type: string;
  enabled: boolean | null;
  role: string | null;
}

export interface TopologyEdge {
  source: string;
  target: string;
  type: string;
}

export interface TopologyView {
  nodes: TopologyNode[];
  edges: TopologyEdge[];
}

let token: string | null = null;

export function setToken(value: string | null): void {
  token = value;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { ...((options.headers as Record<string, string>) ?? {}) };
  if (options.body) {
    headers["Content-Type"] = "application/json";
  }
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  const response = await fetch(path, { ...options, headers });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error((body && body.error) || response.statusText);
  }
  return body as T;
}

export function login(username: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export function listDataSources(): Promise<DataSourceView[]> {
  return request<DataSourceView[]>("/api/data-sources");
}

export function createDataSource(dataSource: DataSourceRequest): Promise<DataSourceView> {
  return request<DataSourceView>("/api/data-sources", {
    method: "POST",
    body: JSON.stringify(dataSource),
  });
}

export function updateDataSource(id: number, dataSource: DataSourceRequest): Promise<DataSourceView> {
  return request<DataSourceView>(`/api/data-sources/${id}`, {
    method: "PUT",
    body: JSON.stringify(dataSource),
  });
}

export function disableDataSource(id: number): Promise<DataSourceView> {
  return request<DataSourceView>(`/api/data-sources/${id}`, { method: "DELETE" });
}

export function discoverTables(dataSourceId: number): Promise<DiscoveredTable[]> {
  return request<DiscoveredTable[]>(`/api/data-sources/${dataSourceId}/tables`);
}

export function listTables(query?: string): Promise<RegisteredTableView[]> {
  const suffix = query ? `?q=${encodeURIComponent(query)}` : "";
  return request<RegisteredTableView[]>(`/api/tables${suffix}`);
}

export function registerTable(table: RegisterTableRequest): Promise<RegisteredTableView> {
  return request<RegisteredTableView>("/api/tables", {
    method: "POST",
    body: JSON.stringify(table),
  });
}

export function unregisterTable(id: number): Promise<void> {
  return request<void>(`/api/tables/${id}`, { method: "DELETE" });
}

export function listPermissions(): Promise<PermissionView[]> {
  return request<PermissionView[]>("/api/permissions");
}

export function grantPermission(permission: PermissionView): Promise<PermissionView[]> {
  return request<PermissionView[]>("/api/permissions", {
    method: "POST",
    body: JSON.stringify(permission),
  });
}

export function revokePermission(permission: PermissionView): Promise<void> {
  return request<void>("/api/permissions/revoke", {
    method: "POST",
    body: JSON.stringify(permission),
  });
}

export function listUsers(): Promise<UserView[]> {
  return request<UserView[]>("/api/admin/users");
}

export function getOverviewStats(): Promise<OverviewStatsView> {
  return request<OverviewStatsView>("/api/overview/stats");
}

export function getTopology(): Promise<TopologyView> {
  return request<TopologyView>("/api/overview/topology");
}

export function startQuery(sql: string): Promise<QueryExecutionView> {
  return request<QueryExecutionView>("/api/queries", {
    method: "POST",
    body: JSON.stringify({ sql }),
  });
}

export function getQuery(queryId: string): Promise<QueryExecutionView> {
  return request<QueryExecutionView>(`/api/queries/${queryId}`);
}

export function cancelQuery(queryId: string): Promise<QueryExecutionView> {
  return request<QueryExecutionView>(`/api/queries/${queryId}/cancel`, { method: "POST" });
}

export async function downloadExport(queryId: string, format: "csv" | "xlsx"): Promise<void> {
  const headers: Record<string, string> = {};
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  const response = await fetch(`/api/queries/${queryId}/export?format=${format}`, { headers });
  if (!response.ok) {
    throw new Error(await response.text());
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `query-${queryId}.${format}`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
