package io.trino.datafabric.stats;

import io.trino.datafabric.query.QueryHistoryView;

import java.util.List;

public record OverviewStatsView(
        long dataSourceCount,
        long enabledDataSourceCount,
        long registeredTableCount,
        long userCount,
        long queryCount,
        List<DataSourceHealthView> dataSourceHealth,
        List<QueryHistoryView> recentQueries) {}
