package io.trino.datafabric.dataset;

import java.util.List;

/**
 * Upstream is what a dataset depends on (input datasets, and physical tables for BASE);
 * downstream is what consumes it (datasets referencing it, plus applications/users from usage).
 */
public record LineageView(
        String datasetUid,
        List<LineageNode> upstream,
        List<LineageNode> downstream)
{
    public record LineageNode(String id, String label, String type)
    {
    }
}
