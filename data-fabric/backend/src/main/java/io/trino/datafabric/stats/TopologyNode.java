package io.trino.datafabric.stats;

public record TopologyNode(
        String id,
        String label,
        String type,
        Boolean enabled,
        String role) {}
