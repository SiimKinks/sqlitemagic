package com.siimkinks.sqlitemagic.intellij.plugin;


import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;

import org.jetbrains.annotations.NotNull;

public final class SqliteMagicLoader implements ApplicationComponent {
    private static final Logger LOG = Logger.getInstance(SqliteMagicLoader.class.getName());

    @NotNull
    @Override
    public String getComponentName() {
        return "SqliteMagic plugin for IntelliJ";
    }

    @Override
    public void initComponent() {
        LOG.info("SqliteMagic plugin initialized for IntelliJ");
    }

    @Override
    public void disposeComponent() {
        LOG.info("SqliteMagic plugin disposed for IntelliJ");
    }
}
