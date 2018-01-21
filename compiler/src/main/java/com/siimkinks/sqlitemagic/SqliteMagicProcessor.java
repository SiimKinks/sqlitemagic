package com.siimkinks.sqlitemagic;

import com.google.auto.service.AutoService;

import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedOptions;

@SupportedOptions({
    "sqlitemagic.generate.logging",
    "sqlitemagic.auto.lib",
    "sqlitemagic.kotlin.public.extensions",
    "sqlitemagic.project.dir",
    "sqlitemagic.db.version",
    "sqlitemagic.db.name"
})
@AutoService(Processor.class)
public class SqliteMagicProcessor extends BaseProcessor {
}
