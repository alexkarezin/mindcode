package info.teksol.mindcode.compiler.optimization;

import info.teksol.mindcode.compiler.instructions.InstructionProcessor;
import info.teksol.mindcode.compiler.instructions.JumpInstruction;
import info.teksol.mindcode.compiler.instructions.LabelInstruction;
import info.teksol.mindcode.compiler.instructions.LabeledInstruction;
import info.teksol.mindcode.compiler.instructions.LogicInstruction;
import info.teksol.mindcode.logic.LogicLabel;

import java.util.ArrayList;
import java.util.List;

/**
 * Remove jumps (both conditional and unconditional) that target the next instruction. The optimization is run
 * repeatedly until it doesn't find jumps to remove, therefore if we have a sequence
 * <pre>{@code
 * 0: jump 2 ...
 * 1: jump 2 ...
 * 2: ...
 * }</pre>
 * both jumps will be eliminated. Such sequences aren't typically generated by the compiler, though.
 */
class SingleStepJumpEliminator extends BaseOptimizer {

    public SingleStepJumpEliminator(InstructionProcessor instructionProcessor) {
        super(instructionProcessor);
    }

    @Override
    protected boolean optimizeProgram() {
        List<JumpInstruction> removableJumps = new ArrayList<>();

        try (LogicIterator iterator = createIterator()) {
            JumpInstruction lastJump = null;
            LogicLabel targetLabel = null;
            boolean isJumpToNext = false;

            while (iterator.hasNext()) {
                LogicInstruction ix = iterator.next();

                if (ix instanceof LabeledInstruction il) {
                    isJumpToNext |= il.getLabel().equals(targetLabel);
                } else {
                    if (isJumpToNext) {
                        removableJumps.add(lastJump);
                        isJumpToNext = false;
                    }

                    if (ix instanceof JumpInstruction jump) {
                        lastJump = jump;
                        targetLabel = jump.getTarget();
                    } else {
                        lastJump = null;
                        targetLabel = null;
                    }
                }
            }
        }

        removableJumps.forEach(this::removeInstruction);
        return true;
    }
}
