package com.siimkinks.sqlitemagic.module;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.processing.GenClassesManagerStep;
import com.siimkinks.sqlitemagic.processing.ModelCodeGenerationStep;
import com.siimkinks.sqlitemagic.processing.ModelCollectionStep;
import com.siimkinks.sqlitemagic.processing.TransformerCodeGenerationStep;
import com.siimkinks.sqlitemagic.processing.TransformerCollectionStep;
import com.siimkinks.sqlitemagic.processing.ViewCodeGenerationStep;
import com.siimkinks.sqlitemagic.processing.ViewCollectionStep;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import lombok.javac.handlers.HandleTable;

@Module(
		injects = {
				TransformerCollectionStep.class,
				TransformerCodeGenerationStep.class,
				ModelCollectionStep.class,
				ModelCodeGenerationStep.class,
				GenClassesManagerStep.class,
				ViewCollectionStep.class,
				ViewCodeGenerationStep.class,
				HandleTable.class,
		},
		library = true
)
public class CompilerModule {

	private final Environment environment;

	public CompilerModule(Environment environment) {
		this.environment = environment;
	}

	@Provides
	@Singleton
	Environment provideEnvironment() {
		return environment;
	}
}
