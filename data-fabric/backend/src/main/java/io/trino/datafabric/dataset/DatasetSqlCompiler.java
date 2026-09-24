package io.trino.datafabric.dataset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Single source of truth for turning a dataset definition into SQL. The same compiled SELECT is
 * used both to create the queryable view in the fabric catalog and (indirectly) to describe the
 * dataset, so the exposed metadata and the queryable structure cannot drift.
 */
@Component
public class DatasetSqlCompiler
{
    private final String fabricCatalog;

    public DatasetSqlCompiler(@Value("${data-fabric.dataset.catalog:fabric}") String fabricCatalog)
    {
        this.fabricCatalog = fabricCatalog;
    }

    /** A referenced input dataset: its alias inside the compiled SQL and its fully-qualified view name. */
    public record InputRef(String alias, String qualifiedName) {}

    public String catalog()
    {
        return fabricCatalog;
    }

    public String viewName(String domain, String name)
    {
        return quote(fabricCatalog) + "." + quote(schema(domain)) + "." + quote(name);
    }

    public String schemaName(String domain)
    {
        return quote(fabricCatalog) + "." + quote(schema(domain));
    }

    public String compileSelect(
            Dataset dataset,
            List<DatasetField> fields,
            DatasetDefinition definition,
            String baseQualifiedName,
            Map<String, InputRef> inputs)
    {
        return switch (dataset.kind()) {
            case BASE -> compileBase(fields, baseQualifiedName);
            case DERIVED -> compileDerived(fields, definition, inputs);
            case AGGREGATE -> compileAggregate(fields, definition, inputs);
        };
    }

    public String compileView(
            Dataset dataset,
            List<DatasetField> fields,
            DatasetDefinition definition,
            String baseQualifiedName,
            Map<String, InputRef> inputs)
    {
        return "CREATE OR REPLACE VIEW " + viewName(dataset.domain(), dataset.name()) + " SECURITY INVOKER AS\n"
                + compileSelect(dataset, fields, definition, baseQualifiedName, inputs);
    }

    private String compileBase(List<DatasetField> fields, String baseQualifiedName)
    {
        if (baseQualifiedName == null || baseQualifiedName.isBlank()) {
            throw new IllegalArgumentException("BASE dataset requires a registered table");
        }
        String projection = fields.isEmpty()
                ? "*"
                : fields.stream().map(field -> quote(field.name())).collect(Collectors.joining(", "));
        return "SELECT " + projection + "\nFROM " + baseQualifiedName;
    }

    private String compileDerived(List<DatasetField> fields, DatasetDefinition definition, Map<String, InputRef> inputs)
    {
        List<String> projection = definition.projections().isEmpty()
                ? fields.stream().map(field -> quote(field.name())).toList()
                : definition.projections();
        if (projection.isEmpty()) {
            throw new IllegalArgumentException("DERIVED dataset requires projections or fields");
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", projection));
        sql.append("\nFROM ").append(fromClause(definition, inputs));
        appendWhere(sql, definition.filters());
        return sql.toString();
    }

    private String compileAggregate(List<DatasetField> fields, DatasetDefinition definition, Map<String, InputRef> inputs)
    {
        if (definition.measures().isEmpty()) {
            throw new IllegalArgumentException("AGGREGATE dataset requires at least one measure");
        }
        List<String> select = new ArrayList<>(definition.groupBy());
        for (DatasetDefinition.MeasureSpec measure : definition.measures()) {
            select.add("%s AS %s".formatted(measure.expression(), quote(measure.name())));
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", select));
        sql.append("\nFROM ").append(fromClause(definition, inputs));
        appendWhere(sql, definition.filters());
        if (!definition.groupBy().isEmpty()) {
            sql.append("\nGROUP BY ").append(String.join(", ", definition.groupBy()));
        }
        return sql.toString();
    }

    private String fromClause(DatasetDefinition definition, Map<String, InputRef> inputs)
    {
        if (definition.inputs().isEmpty()) {
            throw new IllegalArgumentException("Dataset requires at least one input");
        }
        InputRef first = requireInput(inputs, definition.inputs().get(0));
        StringBuilder from = new StringBuilder(first.qualifiedName() + " AS " + quote(first.alias()));
        for (DatasetDefinition.JoinSpec join : definition.joins()) {
            InputRef right = requireInput(inputs, join.rightDatasetUid());
            String leftAlias = requireInput(inputs, join.leftDatasetUid()).alias();
            from.append("\n")
                    .append(join.joinType() == null ? "INNER" : join.joinType().toUpperCase(Locale.ROOT))
                    .append(" JOIN ").append(right.qualifiedName()).append(" AS ").append(quote(right.alias()))
                    .append(" ON ").append(quote(leftAlias)).append(".").append(quote(join.leftField()))
                    .append(" = ").append(quote(right.alias())).append(".").append(quote(join.rightField()));
        }
        return from.toString();
    }

    private static void appendWhere(StringBuilder sql, List<String> filters)
    {
        if (!filters.isEmpty()) {
            sql.append("\nWHERE ").append(String.join(" AND ", filters));
        }
    }

    private static InputRef requireInput(Map<String, InputRef> inputs, String uid)
    {
        InputRef ref = inputs.get(uid);
        if (ref == null) {
            throw new IllegalArgumentException("Unknown input dataset: " + uid);
        }
        return ref;
    }

    public static String schema(String domain)
    {
        if (domain == null || domain.isBlank()) {
            return "public";
        }
        return sanitize(domain);
    }

    private static String sanitize(String value)
    {
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (normalized.isEmpty() || !Character.isLetter(normalized.charAt(0))) {
            normalized = "d_" + normalized;
        }
        return normalized.length() > 60 ? normalized.substring(0, 60) : normalized;
    }

    public static String quote(String identifier)
    {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
