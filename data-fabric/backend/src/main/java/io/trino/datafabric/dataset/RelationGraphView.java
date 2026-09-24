package io.trino.datafabric.dataset;

import java.util.List;

public record RelationGraphView(List<Node> nodes, List<DatasetRelationView> edges)
{
    public record Node(String uid, String name, String kind, String status)
    {
    }
}
