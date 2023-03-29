package info.teksol.mindcode.mindustry;

import info.teksol.mindcode.mindustry.instructions.LogicInstruction;
import info.teksol.mindcode.mindustry.logic.Opcode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogicInstructionLabelResolver {
    public static List<LogicInstruction> resolve(List<LogicInstruction> program) {
        final Map<String, Integer> addresses = calculateAddresses(program);
        return resolveAddresses(program, addresses);
    }

    private static List<LogicInstruction> resolveAddresses(List<LogicInstruction> program, Map<String, Integer> addresses) {
        final List<LogicInstruction> result = new ArrayList<>();
        for (final LogicInstruction instruction : program) {
            if (instruction.isLabel()) continue;
            switch (instruction.getOpcode()) {
                case JUMP:
                    final String label = instruction.getArgs().get(0);
                    if (!addresses.containsKey(label)) {
                        throw new CompilerException("Unknown jump label target: [" + label + "] was not previously discovered in " + program);
                    }

                    resolveJump(label, instruction, addresses, result);
                    break;

                case SET:
                    if (addresses.containsKey(instruction.getArgs().get(1))) {
                        result.add(new LogicInstruction(Opcode.SET, instruction.getArgs().get(0), addresses.get(instruction.getArgs().get(1)).toString()));
                    } else {
                        result.add(instruction);
                    }
                    break;



                case WRITE:
                    if (addresses.containsKey(instruction.getArgs().get(0))) {
                        result.add(new LogicInstruction(Opcode.WRITE, addresses.get(instruction.getArgs().get(0)).toString(), instruction.getArgs().get(1), instruction.getArgs().get(2)));
                    } else {
                        result.add(instruction);
                    }
                    break;



                default:
                    result.add(instruction);
                    break;
            }
        }

        return result;
    }

    private static void resolveJump(String label, LogicInstruction instruction, Map<String, Integer> addresses, List<LogicInstruction> result) {
        final List<String> newArgs = new ArrayList<>(instruction.getArgs().subList(1, instruction.getArgs().size()));
        newArgs.add(0, addresses.get(label).toString());
        result.add(new LogicInstruction(Opcode.JUMP, newArgs));
    }

    private static Map<String, Integer> calculateAddresses(List<LogicInstruction> program) {
        final Map<String, Integer> result = new HashMap<>();
        int instructionPointer = 0;
        for (int i = 0; i < program.size(); i++) {
            final LogicInstruction instruction = program.get(i);
            if (!instruction.isLabel()) {
                instructionPointer++;
                continue;
            }

            final String label = instruction.getArgs().get(0);
            if (result.containsKey(label)) {
                throw new CompilerException("Duplicate label detected: [" + label + "] reused at least twice in " + program);
            }

            result.put(label, instructionPointer);
        }

        return result;
    }
}
