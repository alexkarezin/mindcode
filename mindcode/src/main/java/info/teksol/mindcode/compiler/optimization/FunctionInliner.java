package info.teksol.mindcode.compiler.optimization;

import info.teksol.mindcode.compiler.MessageLevel;
import info.teksol.mindcode.compiler.generator.CallGraph;
import info.teksol.mindcode.compiler.instructions.*;
import info.teksol.mindcode.compiler.optimization.OptimizationContext.LogicList;

import java.util.ArrayList;
import java.util.List;

import static info.teksol.mindcode.compiler.instructions.AstSubcontextType.*;

/**
 * Inlines functions
 */
public class FunctionInliner extends BaseOptimizer {
    public FunctionInliner(OptimizationContext optimizationContext) {
        super(Optimization.FUNCTION_INLINING, optimizationContext);
    }

    private int invocations = 0;
    private int count = 0;

    @Override
    public void generateFinalMessages() {
        iterations = invocations;
        super.generateFinalMessages();
        if (count > 0) {
            emitMessage(MessageLevel.INFO, "%6d function calls inlined by %s.", count, getName());
        }
    }

    @Override
    protected boolean optimizeProgram(OptimizationPhase phase, int pass, int iteration) {
        return false;
    }

    @Override
    public List<OptimizationAction> getPossibleOptimizations(int costLimit) {
        invocations++;
        List<OptimizationAction> actions = new ArrayList<>();
        actions.addAll(forEachContext(AstContextType.FUNCTION, BODY,
                context -> findPossibleInlining(context, costLimit)));
        if (aggressive()) {
            actions.addAll(forEachContext(c -> c.matches(OUT_OF_LINE_CALL),
                    context -> findPossibleCallInlining(context, costLimit)));
        }
        return actions;
    }



    private OptimizationAction findPossibleInlining(AstContext context, int costLimit) {
        if (context.functionPrefix() == null) {
            // The function is declared, but not used.
            return null;
        }
        CallGraph.Function function = getCallGraph().getFunctionByPrefix(context.functionPrefix());
        if (function.isRecursive() || function.isInline()) {
            return null;
        }

        List<AstContext> calls = contexts(c -> c.matches(AstContextType.CALL, OUT_OF_LINE_CALL)
                && c.functionPrefix().equals(context.functionPrefix()));

        // Benefit: saving 3 instructions (set return address, call, return) + half of number of parameters per call
        double benefit = calls.stream().mapToDouble(AstContext::weight).sum() * (3d + function.getParamCount() / 2d);

        // Cost: body size minus one (return) times number of calls minus body size (we'll remove the original)
        LogicList body = stripReturnInstructions(contextInstructions(context));
        if (body == null) {
            return null;
        }

        int bodySize = body.stream().mapToInt(LogicInstruction::getRealSize).sum();
        int cost = (bodySize - 1) * calls.size() - bodySize;

        return cost <= costLimit ? new InlineFunctionAction(context, cost, benefit) : null;
    }

    private LogicList stripReturnInstructions(LogicList body) {
        if (body == null || body.isEmpty()) {
            return null;
        }

        int size = body.getLast() instanceof EndInstruction
                ? body.getFromEnd(1) instanceof GotoInstruction ? body.size() - 2 : -1
                : body.getLast() instanceof GotoInstruction ? body.size() - 1 : -1;
        return size < 0 ? null : body.subList(0, size);
    }

    private OptimizationResult inlineFunction(AstContext context, int costLimit) {
        CallGraph.Function function = getCallGraph().getFunctionByPrefix(context.functionPrefix());
        if (function.isRecursive() || function.isInline()) {
            return OptimizationResult.INVALID;
        }

        LogicList body = stripReturnInstructions(contextInstructions(context));
        if (body == null) {
            return OptimizationResult.INVALID;
        }

        List<AstContext> calls = contexts(c -> c.matches(OUT_OF_LINE_CALL)
                && c.functionPrefix().equals(context.functionPrefix()));

        for (AstContext call : calls) {
            AstContext newContext = call.parent().createSubcontext(INLINE_CALL, 1.0);
            int insertionPoint = firstInstructionIndex(call);
            insertInstructions(insertionPoint, body.duplicateToContext(newContext));
            // Remove original call instructions
            removeMatchingInstructions(ix -> ix.belongsTo(call));
        }

        // Remove original function
        removeMatchingInstructions(ix -> ix.belongsTo(context));

        function.setInlined();
        count += calls.size();

        return OptimizationResult.REALIZED;
    }

    private class InlineFunctionAction extends AbstractOptimizationAction {
        public InlineFunctionAction(AstContext astContext, int cost, double benefit) {
            super(astContext, cost, benefit);
        }

        @Override
        public OptimizationResult apply(int costLimit) {
            return applyOptimization(() -> inlineFunction(astContext(), costLimit), toString());
        }

        @Override
        public String toString() {
            return getName() + ": inline function " + getCallGraph().getFunctionByPrefix(astContext.functionPrefix()).getName();
        }
    }



    private OptimizationAction findPossibleCallInlining(AstContext call, int costLimit) {
        if (call.functionPrefix() == null) {
            // Shouldn't happen here
            return null;
        }
        CallGraph.Function function = getCallGraph().getFunctionByPrefix(call.functionPrefix());
        if (function.isRecursive() || function.isInline()) {
            return null;
        }

        // Benefit: saving 3 instructions (set return address, call, return) + half of number of parameters per call
        double benefit = call.weight() * (3d + function.getParamCount() / 2d);

        // Need to find the function body
        LogicList body = stripReturnInstructions(
                contextInstructions(
                        context(c -> c.matches(AstContextType.FUNCTION, BODY)
                                && c.functionPrefix().equals(call.functionPrefix()))
                )
        );
        if (body == null) {
            return null;
        }
        // Cost: body size minus one (return) times number of calls minus body size (we'll remove the original)
        int cost = body.stream().mapToInt(LogicInstruction::getRealSize).sum() - 1;
        return cost <= costLimit ? new InlineFunctionCallAction(call, cost, benefit) : null;
    }

    private OptimizationResult inlineFunctionCall(AstContext call, int costLimit) {
        CallGraph.Function function = getCallGraph().getFunctionByPrefix(call.functionPrefix());
        if (function.isRecursive() || function.isInline()) {
            return OptimizationResult.INVALID;
        }

        LogicList body = stripReturnInstructions(
                contextInstructions(
                        context(c -> c.matches(AstContextType.FUNCTION, BODY)
                                && c.functionPrefix().equals(call.functionPrefix()))
                )
        );
        if (body == null) {
            return OptimizationResult.INVALID;
        }

        AstContext newContext = call.parent().createSubcontext(INLINE_CALL, 1.0);
        int insertionPoint = firstInstructionIndex(call);
        insertInstructions(insertionPoint, body.duplicateToContext(newContext));
        // Remove original call instructions
        removeMatchingInstructions(ix -> ix.belongsTo(call));

        count += 1;
        return OptimizationResult.REALIZED;
    }

    private class InlineFunctionCallAction extends AbstractOptimizationAction {
        public InlineFunctionCallAction(AstContext astContext, int cost, double benefit) {
            super(astContext, cost, benefit);
        }

        @Override
        public OptimizationResult apply(int costLimit) {
            return applyOptimization(() -> inlineFunctionCall(astContext(), costLimit), toString());
        }

        @Override
        public String toString() {
            return getName() + ": inline function call at line " + astContext.node().startToken().getLine();
        }
    }
}
