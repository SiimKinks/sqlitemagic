package com.siimkinks.sqlitemagic.intellij.plugin.extension;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.siimkinks.sqlitemagic.intellij.plugin.processor.Processor;

import java.util.ArrayList;
import java.util.Collection;

public class SqliteMagicProcessorExtensionPoint {
    public static final ExtensionPointName<Processor> EP_NAME = ExtensionPointName.create("com.siimkinks.sqlitemagic.processor");

    private static Collection<String> SQLITEMAGIC_ANNOTATIONS;

    public static Collection<String> getAllOfProcessedSqliteMagicAnnotation() {
        if (null != SQLITEMAGIC_ANNOTATIONS) {
            return SQLITEMAGIC_ANNOTATIONS;
        }

        Collection<String> arrayList = new ArrayList<String>();
        for (Processor processor : EP_NAME.getExtensions()) {
            arrayList.add(processor.getSupportedAnnotation());
        }

        SQLITEMAGIC_ANNOTATIONS = arrayList;
        return SQLITEMAGIC_ANNOTATIONS;
    }
}
