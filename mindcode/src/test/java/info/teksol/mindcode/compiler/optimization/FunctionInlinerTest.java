package info.teksol.mindcode.compiler.optimization;

import info.teksol.mindcode.compiler.CompilerProfile;
import info.teksol.mindcode.compiler.GenerationGoal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static info.teksol.mindcode.logic.Opcode.END;
import static info.teksol.mindcode.logic.Opcode.PRINT;

class FunctionInlinerTest extends AbstractOptimizerTest<FunctionInliner> {

    @Override
    protected Class<FunctionInliner> getTestedClass() {
        return FunctionInliner.class;
    }

    @Override
    protected List<Optimization> getAllOptimizations() {
        return Optimization.LIST;
    }

    @Override
    protected CompilerProfile createCompilerProfile() {
        return super.createCompilerProfile().setGoal(GenerationGoal.SPEED);
    }

    @Test
    void inlinesFunction() {
        assertCompilesTo("""
                        def foo(n)
                            print(2 * n)
                        end
                                                
                        foo(1)
                        foo(2)
                        """,
                createInstruction(PRINT, q("24")),
                createInstruction(END)
        );
    }

    @Test
    void inlinesTwoFunction() {
        assertCompilesTo("""
                        def foo(n)
                            print(2 * n)
                        end
                                                
                        def bar(n)
                            foo(n)
                            foo(n + 1)
                        end
                                                
                        bar(1)
                        bar(3)
                        """,
                createInstruction(PRINT, q("2468")),
                createInstruction(END)
        );
    }

    @Test
    void inlinesFunctionInsideLoop() {
        assertCompilesTo("""
                        def foo(n)
                            print(n / 2)
                            sum = 0
                            for i in 0 .. n
                                sum += i
                            end
                            return sum
                        end
                                                
                        for i in 0 ... 10
                            foo(2 * i)
                        end
                        foo(0)
                        """,
                createInstruction(PRINT, q("01234567890")),
                createInstruction(END)
        );
    }

    @Test
    void inlinesNestedFunctionCalls() {
        assertCompilesTo("""
                        def foo(n)
                            print(n + 1)
                        end
                                                
                        foo(foo(1))
                        """,
                createInstruction(PRINT, q("23")),
                createInstruction(END)
        );
    }
}