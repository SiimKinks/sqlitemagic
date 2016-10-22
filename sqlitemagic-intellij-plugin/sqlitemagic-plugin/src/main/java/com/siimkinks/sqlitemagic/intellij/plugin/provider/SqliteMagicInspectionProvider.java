package com.siimkinks.sqlitemagic.intellij.plugin.provider;

import com.intellij.codeInspection.InspectionToolProvider;
import com.siimkinks.sqlitemagic.intellij.plugin.inspection.SqliteMagicInspection;

public class SqliteMagicInspectionProvider implements InspectionToolProvider {
  @Override
  public Class[] getInspectionClasses() {
    return new Class[]{SqliteMagicInspection.class};
  }
}
