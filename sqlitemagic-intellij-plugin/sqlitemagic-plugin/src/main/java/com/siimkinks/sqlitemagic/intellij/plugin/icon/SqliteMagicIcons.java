package com.siimkinks.sqlitemagic.intellij.plugin.icon;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class SqliteMagicIcons {
    private static Icon load(String path) {
        return IconLoader.getIcon(path, SqliteMagicIcons.class);
    }

    public static final Icon CLASS_ICON = load("/icons/nodes/class.png");
    public static final Icon FIELD_ICON = load("/icons/nodes/field.png");
    public static final Icon METHOD_ICON = load("/icons/nodes/method.png");

    public static final Icon CONFIG_FILE_ICON = load("/icons/config.png");
}
