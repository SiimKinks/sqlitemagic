package com.siimkinks.sqlitemagic.writer;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.element.TransformerElement;

import java.io.IOException;

import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author Siim Kinks
 */
@Singleton
public class TransformerWriter {

	@Inject
	public TransformerWriter() {
	}

	public void writeSource(Environment environment) throws IOException {
		writeTransformerColumns(environment);
	}

	private void writeTransformerColumns(Environment environment) throws IOException {
		final Filer filer = environment.getFiler();
		for (TransformerElement transformerElement : environment.getTransformerElements().values()) {
			ColumnClassWriter
					.from(transformerElement, environment)
					.write(filer);
		}
	}
}
