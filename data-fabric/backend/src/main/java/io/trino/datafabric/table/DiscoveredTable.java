package io.trino.datafabric.table;

/** A table found in a data source during discovery (not yet registered). */
public record DiscoveredTable(String schema, String table, String type) {}
