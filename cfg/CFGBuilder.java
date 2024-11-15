import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
// import org.objectweb.asm.util.Printer;

import java.io.File;
import java.nio.file.Files;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.Comparator;

public class CFGBuilder {

    static class BasicBlock {
        int id;
        List<AbstractInsnNode> instructions = new ArrayList<>();
        List<BasicBlock> successors = new ArrayList<>();
        Set<BasicBlock> predecessors = new HashSet<>();
    }

    public static void main(String[] args) throws Exception {
        String inputClassPath = args[0];
        String outputCFGPath = args[1];

        // Read the class file
        byte[] classBytes = Files.readAllBytes(new File(inputClassPath).toPath());
        ClassReader classReader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();

        classReader.accept(classNode, 0);

        // Find the main method
        MethodNode mainMethod = null;
        for (Object obj : classNode.methods) {
            MethodNode method = (MethodNode) obj;
            if (method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V")) {
                mainMethod = method;
                break;
            }
        }

        if (mainMethod == null) {
            System.out.println("No main method found in the class.");
            return;
        }

        // Build the CFG
        List<BasicBlock> basicBlocks = buildCFG(mainMethod);

        // Assign IDs using pre-order traversal
        Map<BasicBlock, Integer> blockIds = assignIds(basicBlocks.get(0));

        // Compute dominators
        Map<BasicBlock, Set<BasicBlock>> dominators = computeDominators(basicBlocks, basicBlocks.get(0));

        // Output the CFG
        outputCFG(blockIds, outputCFGPath);

        // Print the dominators
        printDominators(dominators);

        System.out.println("CFG has been successfully written to " + outputCFGPath);
    }

    private static List<BasicBlock> buildCFG(MethodNode methodNode) {
        InsnList instructions = methodNode.instructions;
        Map<LabelNode, BasicBlock> labelToBlock = new HashMap<>();
        List<BasicBlock> blocks = new ArrayList<>();

        // Identify leaders
        Set<AbstractInsnNode> leaders = new HashSet<>();
        // Rule I (The 1st instruction)
        if (instructions.getFirst() != null) {
            leaders.add(instructions.getFirst());
        }

        AbstractInsnNode insn = instructions.getFirst();
        while (insn != null) {
            int opcode = insn.getOpcode();
            if (!(insn instanceof LabelNode) && insn.getOpcode() == -1) {
                insn = insn.getNext();
                continue;
            }

            if (insn instanceof JumpInsnNode) {
                // Rule II and III: Handle jump instructions
                JumpInsnNode jumpInsn = (JumpInsnNode) insn;
                leaders.add(jumpInsn.label);

                int op = insn.getOpcode();
                if (isConditionalJump(op) && insn.getNext() != null) {
                    leaders.add(insn.getNext());
                }
            }

            insn = insn.getNext();
        }

        // Build basic blocks
        insn = instructions.getFirst();
        BasicBlock currentBlock = null;
        while (insn != null) {
            int opcode = insn.getOpcode();
            if (!(insn instanceof LabelNode) && insn.getOpcode() == -1) {
                insn = insn.getNext();
                continue;
            }
            if (leaders.contains(insn)) {
                currentBlock = new BasicBlock();
                blocks.add(currentBlock);
            }
            if (currentBlock != null) {
                if (insn instanceof LabelNode) {
                    labelToBlock.put((LabelNode) insn, currentBlock);
                }
                currentBlock.instructions.add(insn);
            }
            insn = insn.getNext();
        }

        // Identify successors
        for (int i = 0; i < blocks.size(); i++) {
            BasicBlock block = blocks.get(i);
            AbstractInsnNode lastInsn = getLastInstruction(block);
            if (lastInsn == null) {
                continue;
            }
            int opcode = lastInsn.getOpcode();

            if (lastInsn instanceof JumpInsnNode) {
                JumpInsnNode jumpInsn = (JumpInsnNode) lastInsn;
                BasicBlock targetBlock = labelToBlock.get(jumpInsn.label);
                if (targetBlock != null) {
                    block.successors.add(targetBlock);
                    targetBlock.predecessors.add(block);
                }

                if (isConditionalJump(opcode)) {
                    // Add fall-through successor
                    if (i + 1 < blocks.size()) {
                        BasicBlock fallThrough = blocks.get(i + 1);
                        block.successors.add(fallThrough);
                        fallThrough.predecessors.add(block);
                    }
                }
            } else if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                // No successors
            } else {
                // Fall-through to the next block
                if (i + 1 < blocks.size()) {
                    BasicBlock successor = blocks.get(i + 1);
                    block.successors.add(successor);
                    successor.predecessors.add(block);
                }
            }
        }
        return blocks;
    }

    private static AbstractInsnNode getLastInstruction(BasicBlock block) {
        List<AbstractInsnNode> instructions = block.instructions;
        for (int i = instructions.size() - 1; i >= 0; i--) {
            AbstractInsnNode insn = instructions.get(i);
            int opcode = insn.getOpcode();
            if (opcode != -1) {
                return insn;
            }
        }
        return null;
    }

    private static boolean isConditionalJump(int opcode) {
        return (opcode >= Opcodes.IFEQ && opcode <= Opcodes.IF_ACMPNE) ||
               opcode == Opcodes.IFNULL ||
               opcode == Opcodes.IFNONNULL;
    }

    private static Map<BasicBlock, Integer> assignIds(BasicBlock entryBlock) {
        Map<BasicBlock, Integer> blockIds = new LinkedHashMap<>();
        Set<BasicBlock> visited = new HashSet<>();
        Deque<BasicBlock> stack = new ArrayDeque<>();
        stack.push(entryBlock);

        int id = 0;
        while (!stack.isEmpty()) {
            BasicBlock block = stack.pop();
            if (!visited.contains(block)) {
                visited.add(block);
                block.id = id;
                blockIds.put(block, id++);
                // Add successors in reverse order to maintain pre-order traversal
                List<BasicBlock> successors = new ArrayList<>(block.successors);
                Collections.reverse(successors);
                for (BasicBlock succ : successors) {
                    stack.push(succ);
                }
            }
        }
        return blockIds;
    }

    private static void outputCFG(Map<BasicBlock, Integer> blockIds, String outputPath) throws Exception {
        try (java.io.FileWriter writer = new java.io.FileWriter(outputPath)) {
            for (Map.Entry<BasicBlock, Integer> entry : blockIds.entrySet()) {
                BasicBlock block = entry.getKey();
                int id = entry.getValue();
                StringBuilder line = new StringBuilder();
                line.append(id).append(" =>");
                Set<Integer> successorIds = new LinkedHashSet<>();
                for (BasicBlock succ : block.successors) {
                    Integer succId = blockIds.get(succ);
                    if (succId != null) {
                        successorIds.add(succId);
                    }
                }
                for (int succId : successorIds) {
                    line.append(" ").append(succId);
                }
                line.append("\n");
                writer.write(line.toString());
            }
        }
    }

    private static Map<BasicBlock, Set<BasicBlock>> computeDominators(List<BasicBlock> blocks, BasicBlock entryBlock) {
        Map<BasicBlock, Set<BasicBlock>> dominators = new HashMap<>();

        // Initialize dominator sets
        for (BasicBlock block : blocks) {
            Set<BasicBlock> doms = new HashSet<>();
            if (block == entryBlock) {
                doms.add(entryBlock);
            } else {
                doms.addAll(blocks);
            }
            dominators.put(block, doms);
        }

        boolean changed = true;

        while (changed) {
            changed = false;
            for (BasicBlock b : blocks) {
                if (b == entryBlock) {
                    continue;
                }

                Set<BasicBlock> newDoms = new HashSet<>();
                newDoms.add(b);

                // Intersect dominator sets of predecessors
                Set<BasicBlock> predDoms = null;
                for (BasicBlock p : b.predecessors) {
                    Set<BasicBlock> pDoms = dominators.get(p);
                    if (pDoms == null) {
                        continue;
                    }
                    if (predDoms == null) {
                        predDoms = new HashSet<>(pDoms);
                    } else {
                        predDoms.retainAll(pDoms);
                    }
                }

                if (predDoms != null) {
                    newDoms.addAll(predDoms);
                }

                // Check if dominator set has changed
                if (!dominators.get(b).equals(newDoms)) {
                    dominators.put(b, newDoms);
                    changed = true;
                }
            }
        }

        return dominators;
    }

    private static void printDominators(Map<BasicBlock, Set<BasicBlock>> dominators) {
        System.out.println("Dominators:");
        for (BasicBlock block : dominators.keySet()) {
            Set<BasicBlock> doms = dominators.get(block);
            String domsStr = doms.stream()
                                .sorted(Comparator.comparingInt(b -> b.id))
                                .map(b -> Integer.toString(b.id))
                                .collect(Collectors.joining(", "));
            System.out.println("Block " + block.id + ": " + domsStr);
        }
    }

    //     private static void outputCFG(Map<BasicBlock, Integer> blockIds, String outputPath) throws Exception {
    //     try (java.io.FileWriter writer = new java.io.FileWriter(outputPath)) {
    //         for (Map.Entry<BasicBlock, Integer> entry : blockIds.entrySet()) {
    //             BasicBlock block = entry.getKey();
    //             int id = entry.getValue();
    //             StringBuilder line = new StringBuilder();
    //             line.append(id).append(" =>");
    //             Set<Integer> successorIds = new LinkedHashSet<>();
    //             for (BasicBlock succ : block.successors) {
    //                 Integer succId = blockIds.get(succ);
    //                 if (succId != null) {
    //                     successorIds.add(succId);
    //                 }
    //             }
    //             for (int succId : successorIds) {
    //                 line.append(" ").append(succId);
    //             }
    //             line.append("\n");
    //             writer.write(line.toString());

    //             // Add block instructions for debugging
    //             writer.write("    Block " + id + " Instructions:\n");
    //             for (AbstractInsnNode insn : block.instructions) {
    //                 String insnStr = insnToString(insn);
    //                 writer.write("        " + insnStr + "\n");
    //             }
    //             writer.write("\n"); // Add an empty line between blocks
    //         }
    //     }
    // }

    // private static String insnToString(AbstractInsnNode insn) {
    //     if (insn instanceof LabelNode) {
    //         return "LABEL";
    //     } else if (insn instanceof JumpInsnNode) {
    //         JumpInsnNode jumpInsn = (JumpInsnNode) insn;
    //         return "JUMP " + Printer.OPCODES[jumpInsn.getOpcode()] + " to LABEL";
    //     } else if (insn instanceof VarInsnNode) {
    //         VarInsnNode varInsn = (VarInsnNode) insn;
    //         return Printer.OPCODES[varInsn.getOpcode()] + " var=" + varInsn.var;
    //     } else if (insn instanceof IntInsnNode) {
    //         IntInsnNode intInsn = (IntInsnNode) insn;
    //         return Printer.OPCODES[intInsn.getOpcode()] + " operand=" + intInsn.operand;
    //     } else if (insn instanceof InsnNode) {
    //         return Printer.OPCODES[insn.getOpcode()];
    //     } else if (insn instanceof IincInsnNode) {
    //         IincInsnNode iincInsn = (IincInsnNode) insn;
    //         return "IINC var=" + iincInsn.var + " by " + iincInsn.incr;
    //     } else if (insn instanceof LdcInsnNode) {
    //         LdcInsnNode ldcInsn = (LdcInsnNode) insn;
    //         return "LDC " + ldcInsn.cst;
    //     } else if (insn instanceof TypeInsnNode) {
    //         TypeInsnNode typeInsn = (TypeInsnNode) insn;
    //         return Printer.OPCODES[typeInsn.getOpcode()] + " type=" + typeInsn.desc;
    //     } else if (insn instanceof MethodInsnNode) {
    //         MethodInsnNode methodInsn = (MethodInsnNode) insn;
    //         return Printer.OPCODES[methodInsn.getOpcode()] + " " + methodInsn.owner + "." + methodInsn.name + methodInsn.desc;
    //     } else if (insn instanceof FieldInsnNode) {
    //         FieldInsnNode fieldInsn = (FieldInsnNode) insn;
    //         return Printer.OPCODES[fieldInsn.getOpcode()] + " " + fieldInsn.owner + "." + fieldInsn.name + " " + fieldInsn.desc;
    //     } else {
    //         return "UNKNOWN_INSTRUCTION";
    //     }
    // }
}