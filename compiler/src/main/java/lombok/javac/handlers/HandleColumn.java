package lombok.javac.handlers;

import com.siimkinks.sqlitemagic.annotation.Column;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;

import org.mangosdk.spi.ProviderFor;

import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleColumn extends JavacAnnotationHandler<Column> {
	@Override
	public void handle(AnnotationValues<Column> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode) {
		// FIXME: 9.04.16 remove
		JavacNode node = annotationNode.up();
		if (node.getKind() == AST.Kind.FIELD) {
			JCTree.JCVariableDecl fieldDec = (JCTree.JCVariableDecl) node.get();
			if ((fieldDec.mods.flags & Flags.PRIVATE) != 0 && (fieldDec.mods.flags & (Flags.STATIC | Flags.FINAL)) == 0) {
				fieldDec.mods.flags &= ~Flags.PRIVATE;
			}
			node.rebuild();
		}
	}
}
