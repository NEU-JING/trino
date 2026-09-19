package io.trino.datafabric.stats;

import java.util.List;

public record TopologyView(List<TopologyNode> nodes, List<TopologyEdge> edges) {}
