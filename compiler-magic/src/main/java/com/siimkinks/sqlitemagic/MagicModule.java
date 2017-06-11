package com.siimkinks.sqlitemagic;

import dagger.Module;
import lombok.javac.handlers.HandleTable;

@Module(
    injects = {HandleTable.class},
    complete = false
)
public class MagicModule {
}
