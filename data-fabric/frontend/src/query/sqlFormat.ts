import { format } from "sql-formatter";

export function formatSql(sql: string): string {
  if (!sql.trim()) {
    return sql;
  }
  try {
    return format(sql, {
      language: "trino",
      keywordCase: "upper",
      tabWidth: 2,
      linesBetweenQueries: 1,
    });
  } catch {
    return sql;
  }
}
