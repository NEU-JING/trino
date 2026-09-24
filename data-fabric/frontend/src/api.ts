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

export interface SuggestionView {
  value: string;
  label: string;
}

export interface ColumnMetadataView {
  name: string;
  type: string;
  nullable: boolean;
  comment: string;
}

export interface SampleRowsView {
  columns: string[];
  rows: unknown[][];
}

export interface TableDetailView {
  id: number;
  catalog: string;
  schema: string;
  table: string;
  description: string;
  registeredBy: string;
  rowCount: number | null;
  principals: string[];
}

export type SuggestionType = "catalog" | "schema" | "table" | "column";

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

export interface QueryHistoryEntry {
  id: number;
  queryId: string;
  user: string;
  sql: string;
  state: string;
  startedAt: string;
  finishedAt: string | null;
  rowCount: number | null;
  truncated: boolean;
  error: string | null;
}

export interface SavedQueryView {
  id: number;
  name: string;
  sql: string;
  createdAt: string;
  updatedAt: string;
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

export function createUser(user: { username: string; password: string; role: string }): Promise<UserView> {
  return request<UserView>("/api/admin/users", {
    method: "POST",
    body: JSON.stringify(user),
  });
}

export function updateUser(username: string, update: { role?: string; enabled?: boolean }): Promise<UserView> {
  return request<UserView>(`/api/admin/users/${encodeURIComponent(username)}`, {
    method: "PATCH",
    body: JSON.stringify(update),
  });
}

export function suggestMetadata(type: SuggestionType, parent?: string, query?: string): Promise<SuggestionView[]> {
  const params = new URLSearchParams({ type });
  if (parent) {
    params.set("parent", parent);
  }
  if (query) {
    params.set("q", query);
  }
  return request<SuggestionView[]>(`/api/metadata/suggest?${params.toString()}`);
}

export function getTableColumns(id: number): Promise<ColumnMetadataView[]> {
  return request<ColumnMetadataView[]>(`/api/tables/${id}/columns`);
}

export function getTableSample(id: number, limit?: number): Promise<SampleRowsView> {
  const suffix = limit ? `?limit=${limit}` : "";
  return request<SampleRowsView>(`/api/tables/${id}/sample${suffix}`);
}

export function getTableDetail(id: number): Promise<TableDetailView> {
  return request<TableDetailView>(`/api/tables/${id}/detail`);
}

export function testDataSourceConnection(dataSource: DataSourceRequest): Promise<void> {
  return request<void>("/api/data-sources/test", {
    method: "POST",
    body: JSON.stringify(dataSource),
  });
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

export function listQueryHistory(limit?: number): Promise<QueryHistoryEntry[]> {
  const suffix = limit ? `?limit=${limit}` : "";
  return request<QueryHistoryEntry[]>(`/api/query-history${suffix}`);
}

export function listSavedQueries(): Promise<SavedQueryView[]> {
  return request<SavedQueryView[]>("/api/saved-queries");
}

export function saveQuery(name: string, sql: string): Promise<SavedQueryView> {
  return request<SavedQueryView>("/api/saved-queries", {
    method: "POST",
    body: JSON.stringify({ name, sql }),
  });
}

export function deleteSavedQuery(id: number): Promise<void> {
  return request<void>(`/api/saved-queries/${id}`, { method: "DELETE" });
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

export interface DatasetFieldView {
  name: string;
  label: string;
  dataType: string | null;
  role: string;
  aggregation: string | null;
  timeGrain: string | null;
  format: string | null;
  unit: string | null;
  nullable: boolean;
}

export interface DatasetMaterializationView {
  mode: string;
  target: string | null;
  status: string | null;
  message: string | null;
  refreshedAt: string | null;
  staleSeconds: number | null;
}

export interface DatasetView {
  id: number;
  uid: string;
  name: string;
  description: string;
  domain: string | null;
  owner: string;
  kind: string;
  status: string;
  currentVersion: number;
  materializationMode: string;
  createdAt: string;
  updatedAt: string;
}

export interface DatasetDetailView extends DatasetView {
  fields: DatasetFieldView[];
  materialization: DatasetMaterializationView | null;
}

export interface DatasetVersionView {
  version: number;
  status: string;
  publishedAt: string | null;
  createdAt: string;
}

export interface DatasetRelationView {
  id: number;
  fromDatasetUid: string;
  fromField: string;
  toDatasetUid: string;
  toField: string;
  joinType: string;
  cardinality: string;
  origin: string;
}

export interface RelationGraphView {
  nodes: { uid: string; name: string; kind: string; status: string }[];
  edges: DatasetRelationView[];
}

export interface LineageView {
  datasetUid: string;
  upstream: { id: string; label: string; type: string }[];
  downstream: { id: string; label: string; type: string }[];
}

export interface DatasetUsageView {
  datasetUid: string;
  application: string;
  username: string;
  queryCount: number;
  failureCount: number;
  averageLatencyMs: number;
  lastUsedAt: string | null;
}

export interface DatasetFieldRequest {
  name: string;
  label?: string;
  dataType?: string;
  role?: string;
  aggregation?: string;
  timeGrain?: string;
  format?: string;
  unit?: string;
  nullable?: boolean;
}

export interface CreateDatasetRequest {
  name: string;
  description?: string;
  domain?: string;
  kind: string;
  baseTableId?: number;
  inputs?: string[];
  joins?: { leftDatasetUid: string; leftField: string; rightDatasetUid: string; rightField: string; joinType: string; cardinality: string }[];
  projections?: string[];
  filters?: string[];
  measures?: { name: string; expression: string; aggregation: string }[];
  groupBy?: string[];
  timeGrain?: string;
  fields?: DatasetFieldRequest[];
}

export function listDatasets(): Promise<DatasetView[]> {
  return request<DatasetView[]>("/api/datasets");
}

export function getDataset(uid: string): Promise<DatasetDetailView> {
  return request<DatasetDetailView>(`/api/datasets/${encodeURIComponent(uid)}`);
}

export function listDatasetVersions(uid: string): Promise<DatasetVersionView[]> {
  return request<DatasetVersionView[]>(`/api/datasets/${encodeURIComponent(uid)}/versions`);
}

export function createDataset(payload: CreateDatasetRequest): Promise<DatasetDetailView> {
  return request<DatasetDetailView>("/api/datasets", { method: "POST", body: JSON.stringify(payload) });
}

export function publishDataset(uid: string): Promise<DatasetDetailView> {
  return request<DatasetDetailView>(`/api/datasets/${encodeURIComponent(uid)}/publish`, { method: "POST" });
}

export function deprecateDataset(uid: string): Promise<DatasetDetailView> {
  return request<DatasetDetailView>(`/api/datasets/${encodeURIComponent(uid)}/deprecate`, { method: "POST" });
}

export function setDatasetMaterialization(uid: string, mode: string): Promise<DatasetDetailView> {
  return request<DatasetDetailView>(`/api/datasets/${encodeURIComponent(uid)}/materialization`, {
    method: "PUT",
    body: JSON.stringify({ mode }),
  });
}

export function refreshDatasetMaterialization(uid: string): Promise<DatasetDetailView> {
  return request<DatasetDetailView>(`/api/datasets/${encodeURIComponent(uid)}/materialization/refresh`, {
    method: "POST",
  });
}

export function listRelations(datasetUid?: string): Promise<DatasetRelationView[]> {
  const suffix = datasetUid ? `?dataset=${encodeURIComponent(datasetUid)}` : "";
  return request<DatasetRelationView[]>(`/api/model/relations${suffix}`);
}

export function relationGraph(): Promise<RelationGraphView> {
  return request<RelationGraphView>("/api/model/graph");
}

export function datasetLineage(uid: string): Promise<LineageView> {
  return request<LineageView>(`/api/model/lineage/${encodeURIComponent(uid)}`);
}

export function inferRelations(): Promise<DatasetRelationView[]> {
  return request<DatasetRelationView[]>("/api/model/relations/infer", { method: "POST" });
}

export function confirmRelation(id: number): Promise<DatasetRelationView> {
  return request<DatasetRelationView>(`/api/model/relations/${id}/confirm`, { method: "POST" });
}

export function listDatasetUsage(): Promise<DatasetUsageView[]> {
  return request<DatasetUsageView[]>("/api/usage/datasets");
}

export function datasetUsage(uid: string): Promise<DatasetUsageView[]> {
  return request<DatasetUsageView[]>(`/api/usage/datasets/${encodeURIComponent(uid)}`);
}
