package io.trino.datafabric.dataset;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetSqlCompilerTest
{
    private final DatasetSqlCompiler compiler = new DatasetSqlCompiler("fabric");

    @Test
    void compilesBaseDataset()
    {
        Dataset dataset = dataset("orders", DatasetKind.BASE, DatasetDefinition.empty());
        String sql = compiler.compileSelect(
                dataset,
                List.of(field("order_id"), field("amount")),
                DatasetDefinition.empty(),
                "\"ob\".\"public\".\"orders\"",
                Map.of());
        assertThat(sql).isEqualTo("SELECT \"order_id\", \"amount\"\nFROM \"ob\".\"public\".\"orders\"");
    }

    @Test
    void compilesDerivedDatasetWithJoin()
    {
        DatasetDefinition definition = new DatasetDefinition(
                null,
                List.of("u1", "u2"),
                List.of(new DatasetDefinition.JoinSpec("u1", "customer_id", "u2", "customer_id", "LEFT", "N:1")),
                List.of("t1.order_id", "t2.name"),
                List.of("t1.status = 'paid'"),
                List.of(),
                List.of(),
                null);
        Dataset dataset = dataset("orders_enriched", DatasetKind.DERIVED, definition);
        String sql = compiler.compileSelect(dataset, List.of(), definition, null, Map.of(
                "u1", new DatasetSqlCompiler.InputRef("t1", "\"fabric\".\"public\".\"orders\""),
                "u2", new DatasetSqlCompiler.InputRef("t2", "\"fabric\".\"public\".\"customers\"")));
        assertThat(sql).contains("FROM \"fabric\".\"public\".\"orders\" AS \"t1\"");
        assertThat(sql).contains("LEFT JOIN \"fabric\".\"public\".\"customers\" AS \"t2\"");
        assertThat(sql).contains("ON \"t1\".\"customer_id\" = \"t2\".\"customer_id\"");
        assertThat(sql).contains("WHERE t1.status = 'paid'");
    }

    @Test
    void compilesAggregateDataset()
    {
        DatasetDefinition definition = new DatasetDefinition(
                null,
                List.of("u1"),
                List.of(),
                List.of(),
                List.of("t1.status = 'paid'"),
                List.of(new DatasetDefinition.MeasureSpec("sales", "sum(t1.amount)", "SUM")),
                List.of("t1.dept_name"),
                "month");
        Dataset dataset = dataset("monthly_sales", DatasetKind.AGGREGATE, definition);
        String sql = compiler.compileSelect(dataset, List.of(), definition, null, Map.of(
                "u1", new DatasetSqlCompiler.InputRef("t1", "\"fabric\".\"public\".\"orders\"")));
        assertThat(sql).contains("SELECT t1.dept_name, sum(t1.amount) AS \"sales\"");
        assertThat(sql).contains("GROUP BY t1.dept_name");
    }

    @Test
    void viewIsInvokerSecurityAndQualified()
    {
        Dataset dataset = dataset("orders", DatasetKind.BASE, DatasetDefinition.empty());
        String view = compiler.compileView(
                dataset,
                List.of(field("order_id")),
                DatasetDefinition.empty(),
                "\"ob\".\"public\".\"orders\"",
                Map.of());
        assertThat(view).startsWith("CREATE OR REPLACE VIEW \"fabric\".\"public\".\"orders\" SECURITY INVOKER AS");
    }

    private static Dataset dataset(String name, DatasetKind kind, DatasetDefinition definition)
    {
        return new Dataset(1, "uid-" + name, name, "", "public", "admin", kind, DatasetStatus.DRAFT, 0,
                MaterializationMode.VIRTUAL, "{}", Instant.now(), Instant.now());
    }

    private static DatasetField field(String name)
    {
        return new DatasetField(0, 1, name, name, "varchar", FieldRole.DIMENSION, null, null, null, null, true, 0);
    }
}
