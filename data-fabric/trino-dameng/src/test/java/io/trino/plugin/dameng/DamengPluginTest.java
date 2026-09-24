package io.trino.plugin.dameng;

import io.trino.spi.connector.ConnectorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DamengPluginTest
{
    @Test
    void exposesDamengConnectorFactory()
    {
        Iterable<ConnectorFactory> factories = new DamengPlugin().getConnectorFactories();
        assertThat(factories)
                .singleElement()
                .extracting(ConnectorFactory::getName)
                .isEqualTo("dameng");
    }
}
