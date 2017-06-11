package com.siimkinks.sqlitemagic;

import com.google.auto.service.AutoService;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;

import dagger.ObjectGraph;

@AutoService(Processor.class)
public class SqliteMagicProcessor extends BaseProcessor {
  @Override
  protected ObjectGraph createObjectGraph(ProcessingEnvironment env) {
    return super.createObjectGraph(env).plus(new MagicModule());
  }
}