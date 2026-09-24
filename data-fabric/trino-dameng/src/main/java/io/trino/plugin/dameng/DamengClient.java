package io.trino.plugin.dameng;

import com.google.inject.Inject;
import io.trino.plugin.base.mapping.IdentifierMapping;
import io.trino.plugin.jdbc.BaseJdbcClient;
import io.trino.plugin.jdbc.BaseJdbcConfig;
import io.trino.plugin.jdbc.ColumnMapping;
import io.trino.plugin.jdbc.ConnectionFactory;
import io.trino.plugin.jdbc.JdbcTypeHandle;
import io.trino.plugin.jdbc.QueryBuilder;
import io.trino.plugin.jdbc.WriteMapping;
import io.trino.plugin.jdbc.logging.RemoteQueryModifier;
import io.trino.spi.TrinoException;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.type.DecimalType;
import io.trino.spi.type.Type;
import io.trino.spi.type.VarcharType;

import java.sql.Connection;
import java.sql.Types;
import java.util.Optional;

import static io.trino.plugin.jdbc.StandardColumnMappings.bigintColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.booleanColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.dateColumnMappingUsingSqlDate;
import static io.trino.plugin.jdbc.StandardColumnMappings.decimalColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.defaultCharColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.defaultVarcharColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.doubleColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.integerColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.realColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.smallintColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.timeColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.timestampColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.tinyintColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.varbinaryColumnMapping;
import static io.trino.plugin.jdbc.StandardColumnMappings.varcharColumnMapping;
import static io.trino.spi.StandardErrorCode.NOT_SUPPORTED;
import static io.trino.spi.type.TimeType.createTimeType;
import static io.trino.spi.type.TimestampType.createTimestampType;
import static io.trino.spi.type.VarcharType.createUnboundedVarcharType;
import static io.trino.spi.type.VarcharType.createVarcharType;

/**
 * Read-only JDBC client for Dameng DM8. Trino identifiers are quoted with double
 * quotes and are case-insensitive on the Dameng side (unquoted identifiers are
 * stored uppercase). Writing is intentionally not supported by this prototype.
 */
public class DamengClient
        extends BaseJdbcClient
{
    private static final int MAX_DECIMAL_PRECISION = 38;

    @Inject
    public DamengClient(
            BaseJdbcConfig config,
            ConnectionFactory connectionFactory,
            QueryBuilder queryBuilder,
            IdentifierMapping identifierMapping,
            RemoteQueryModifier queryModifier)
    {
        super("\"", connectionFactory, queryBuilder, config.getJdbcTypesMappedToVarchar(), identifierMapping, queryModifier, false);
    }

    @Override
    public Optional<ColumnMapping> toColumnMapping(ConnectorSession session, Connection connection, JdbcTypeHandle typeHandle)
    {
        Optional<ColumnMapping> forcedMapping = getForcedMappingToVarchar(typeHandle);
        if (forcedMapping.isPresent()) {
            return forcedMapping;
        }

        return switch (typeHandle.jdbcType()) {
            case Types.BIT, Types.BOOLEAN -> Optional.of(booleanColumnMapping());
            case Types.TINYINT -> Optional.of(tinyintColumnMapping());
            case Types.SMALLINT -> Optional.of(smallintColumnMapping());
            case Types.INTEGER -> Optional.of(integerColumnMapping());
            case Types.BIGINT -> Optional.of(bigintColumnMapping());
            case Types.REAL, Types.FLOAT -> Optional.of(realColumnMapping());
            case Types.DOUBLE -> Optional.of(doubleColumnMapping());
            case Types.DECIMAL, Types.NUMERIC -> Optional.of(decimalColumnMapping(decimalType(typeHandle)));
            case Types.CHAR, Types.NCHAR -> Optional.of(charMapping(typeHandle));
            case Types.VARCHAR, Types.NVARCHAR, Types.LONGVARCHAR, Types.LONGNVARCHAR -> Optional.of(varcharMapping(typeHandle));
            // Dameng CLOB/TEXT is exposed as a character column; degrade to unbounded varchar (D4).
            case Types.CLOB, Types.NCLOB -> Optional.of(varcharColumnMapping(createUnboundedVarcharType(), false));
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> Optional.of(varbinaryColumnMapping());
            case Types.DATE -> Optional.of(dateColumnMappingUsingSqlDate());
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> Optional.of(timeColumnMapping(createTimeType(precision(typeHandle))));
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> Optional.of(timestampColumnMapping(createTimestampType(precision(typeHandle))));
            default -> Optional.empty();
        };
    }

    @Override
    public WriteMapping toWriteMapping(ConnectorSession session, Type type)
    {
        throw new TrinoException(NOT_SUPPORTED, "Writing is not supported by the Dameng connector");
    }

    private static DecimalType decimalType(JdbcTypeHandle typeHandle)
    {
        int precision = typeHandle.columnSize().orElse(MAX_DECIMAL_PRECISION);
        precision = Math.max(1, Math.min(precision, MAX_DECIMAL_PRECISION));
        int scale = typeHandle.decimalDigits().orElse(0);
        scale = Math.max(0, Math.min(scale, precision));
        return DecimalType.createDecimalType(precision, scale);
    }

    private static ColumnMapping charMapping(JdbcTypeHandle typeHandle)
    {
        int length = typeHandle.columnSize().orElse(1);
        return defaultCharColumnMapping(length, false);
    }

    private static ColumnMapping varcharMapping(JdbcTypeHandle typeHandle)
    {
        int length = typeHandle.columnSize().orElse(0);
        if (length <= 0 || length > VarcharType.MAX_LENGTH) {
            return varcharColumnMapping(createUnboundedVarcharType(), false);
        }
        return varcharColumnMapping(createVarcharType(length), false);
    }

    private static int precision(JdbcTypeHandle typeHandle)
    {
        return Math.max(0, Math.min(typeHandle.decimalDigits().orElse(6), 9));
    }
}
