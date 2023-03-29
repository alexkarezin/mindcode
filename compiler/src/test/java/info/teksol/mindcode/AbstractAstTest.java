package info.teksol.mindcode;

import info.teksol.mindcode.ast.AstNode;
import info.teksol.mindcode.ast.AstNodeBuilder;
import info.teksol.mindcode.ast.AstPrettyPrinter;
import info.teksol.mindcode.grammar.AbstractParserTest;
import info.teksol.mindcode.mindustry.instructions.LogicInstruction;

import java.util.List;

public class AbstractAstTest extends AbstractParserTest {
    public AstNode translateToAst(String program) {
        return AstNodeBuilder.generate(parse(program));
    }

    protected final String prettyPrint(List<LogicInstruction> list) {
        return list.stream().map(Object::toString).reduce("", (s, s2) -> s + "\n" + s2).strip();
    }

    protected String prettyPrint(AstNode node) {
        return new AstPrettyPrinter().prettyPrint(node);
    }
}
