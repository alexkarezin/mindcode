package info.teksol.mindcode.compiler.optimization;

import org.junit.jupiter.api.Test;

import java.util.List;

import static info.teksol.mindcode.compiler.optimization.Optimization.*;
import static info.teksol.mindcode.logic.Opcode.*;

public class UnreachableCodeEliminatorTest extends AbstractOptimizerTest<UnreachableCodeEliminator> {

    @Override
    protected Class<UnreachableCodeEliminator> getTestedClass() {
        return UnreachableCodeEliminator.class;
    }

    @Override
    protected List<Optimization> getAllOptimizations() {
        return List.of(
                CONDITIONAL_JUMPS_NORMALIZATION,
                DEAD_CODE_ELIMINATION,
                SINGLE_STEP_JUMP_ELIMINATION,
                JUMP_TARGET_PROPAGATION,
                UNREACHABLE_CODE_ELIMINATION
        );
    }

    @Test
    void removesOrphanedJump() {
        assertCompilesTo("""
                        while a
                            while b
                                print(b)
                            end
                        end
                        """,
                createInstruction(LABEL, "__start__"),
                createInstruction(LABEL, var(1000)),
                createInstruction(JUMP, "__start__", "equal", "a", "false"),
                createInstruction(LABEL, var(1003)),
                createInstruction(JUMP, var(1000), "equal", "b", "false"),
                createInstruction(PRINT, "b"),
                createInstruction(JUMP, var(1003), "always")
        );
    }

    @Test
    void eliminateDeadBranch() {
        assertCompilesTo("""
                        print(a)
                        while false
                            print(b)
                        end
                        print(c)
                        """,
                createInstruction(PRINT, "a"),
                createInstruction(PRINT, "c"),
                createInstruction(END)
        );
    }

    @Test
    void eliminateUnusedFunction() {
        assertCompilesTo("""
                        def a
                            print("here")
                        end
                        while false
                            a()
                            a()
                        end
                        print("Done")
                        """,
                createInstruction(PRINT, q("Done")),
                createInstruction(END)
        );
    }

    @Test
    void keepsUsedFunctions() {
        assertCompilesToWithMessages(ignore("List of unused variables: testa.n, testb.n, testc.n."),
                """
                        allocate stack in cell1[0 .. 63]
                        def testa(n)
                            print("Start")
                        end
                        def testb(n)
                            print("Middle")
                        end
                        def testc(n)
                            print("End")
                        end
                        testa(0)
                        testa(0)
                        while false
                            testb(1)
                            testb(1)
                        end
                        testc(2)
                        testc(2)
                        printflush(message1)
                        """,
                // call testa (2x)
                createInstruction(SETADDR, "__fn2retaddr", var(1003)),
                createInstruction(CALL, var(1002)),
                createInstruction(GOTOLABEL, var(1003), "__fn2"),
                createInstruction(SETADDR, "__fn2retaddr", var(1004)),
                createInstruction(CALL, var(1002)),
                createInstruction(GOTOLABEL, var(1004), "__fn2"),
                // if false + call testb -- removed
                // call testc (2)
                createInstruction(SETADDR, "__fn1retaddr", var(1010)),
                createInstruction(CALL, var(1001)),
                createInstruction(GOTOLABEL, var(1010), "__fn1"),
                createInstruction(SETADDR, "__fn1retaddr", var(1011)),
                createInstruction(CALL, var(1001)),
                createInstruction(GOTOLABEL, var(1011), "__fn1"),
                createInstruction(PRINTFLUSH, "message1"),
                createInstruction(END),
                // def testb -- removed
                // def testc
                createInstruction(LABEL, var(1001)),
                createInstruction(PRINT, "\"End\""),
                createInstruction(GOTO, "__fn1retaddr", "__fn1"),
                // def testa
                createInstruction(LABEL, var(1002)),
                createInstruction(PRINT, "\"Start\""),
                createInstruction(GOTO, "__fn2retaddr", "__fn2")
        );
    }

    @Test
    void eliminatesSelfReferencedJumps() {
        assertCompilesTo("""
                        while true
                           print("foo")
                           printflush(message1)
                        end
                        print("WooHoo!")
                        """,
                createInstruction(LABEL, var(1000)),
                createInstruction(PRINT, q("foo")),
                createInstruction(PRINTFLUSH, "message1"),
                createInstruction(JUMP, var(1000), "always")
        );
    }
}
