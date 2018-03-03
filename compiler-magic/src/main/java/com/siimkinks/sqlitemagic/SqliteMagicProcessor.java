package com.siimkinks.sqlitemagic;

import com.google.auto.service.AutoService;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedOptions;

import dagger.ObjectGraph;

@SupportedOptions({
    "sqlitemagic.generate.logging",
    "sqlitemagic.auto.lib",
    "sqlitemagic.kotlin.public.extensions",
    "sqlitemagic.project.dir",
    "sqlitemagic.variant.debug",
    "sqlitemagic.variant.dir.name",
    "sqlitemagic.db.version",
    "sqlitemagic.db.name"
})
@AutoService(Processor.class)
public class SqliteMagicProcessor extends BaseProcessor {
  @Override
  protected ObjectGraph createObjectGraph(ProcessingEnvironment env) {
    return super.createObjectGraph(env).plus(new MagicModule());
  }
}