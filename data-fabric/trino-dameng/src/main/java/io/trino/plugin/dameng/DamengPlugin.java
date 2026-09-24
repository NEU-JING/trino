package io.trino.plugin.dameng;

import io.trino.plugin.jdbc.JdbcPlugin;

public class DamengPlugin
        extends JdbcPlugin
{
    public DamengPlugin()
    {
        super("dameng", DamengClientModule::new);
    }
}
