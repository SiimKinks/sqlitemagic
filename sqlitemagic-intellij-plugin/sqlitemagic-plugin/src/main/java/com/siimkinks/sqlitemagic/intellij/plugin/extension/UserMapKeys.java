package com.siimkinks.sqlitemagic.intellij.plugin.extension;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;

public class UserMapKeys {
    private static final String SQLITEMAGIC_IS_PRESENT_PROPERTY = "sqlitemagic.annotation.present";

    public static final Key<Boolean> HAS_SQLITEMAGIC_KEY = Key.create(SQLITEMAGIC_IS_PRESENT_PROPERTY);

    public static void updateSqliteMagicPresent(@NotNull UserDataHolder element, boolean isPresent) {
        element.putUserData(HAS_SQLITEMAGIC_KEY, isPresent);
    }

    public static boolean isSqliteMagicPossiblePresent(@NotNull UserDataHolder element) {
        Boolean userData = element.getUserData(HAS_SQLITEMAGIC_KEY);
        return null == userData || userData;
    }
}
