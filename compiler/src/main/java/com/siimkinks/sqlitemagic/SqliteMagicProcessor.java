package com.siimkinks.sqlitemagic;

import com.google.auto.service.AutoService;

import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedOptions;

import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.AGGREGATING;

@SupportedOptions({
    "sqlitemagic.generate.logging",
    "sqlitemagic.auto.lib",
    "sqlitemagic.kotlin.public.extensions",
    "sqlitemagic.project.dir",
    "sqlitemagic.variant.debug",
    "sqlitemagic.variant.name",
    "sqlitemagic.migrate.debug",
    "sqlitemagic.main.module.path",
    "sqlitemagic.db.version",
    "sqlitemagic.db.name"
})
@AutoService(Processor.class)
@IncrementalAnnotationProcessor(AGGREGATING)
public class SqliteMagicProcessor extends BaseProcessor {
}
