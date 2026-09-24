package io.trino.datafabric.datasource;

/**
 * Business-facing data source types. The display name is what users see; the
 * connector name is never exposed by the API.
 */
public enum BusinessType
{
    OCEANBASE("mysql", "OceanBase"),
    GREENPLUM("postgresql", "Greenplum"),
    DAMENG("dameng", "达梦");

    private final String connectorName;
    private final String displayName;

    BusinessType(String connectorName, String displayName)
    {
        this.connectorName = connectorName;
        this.displayName = displayName;
    }

    public String connectorName()
    {
        return connectorName;
    }

    public String displayName()
    {
        return displayName;
    }

    public static BusinessType from(String value)
    {
        if (value == null) {
            throw new IllegalArgumentException("businessType is required");
        }
        for (BusinessType type : values()) {
            if (type.displayName.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown business type: " + value);
    }
}
