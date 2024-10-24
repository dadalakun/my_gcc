import gov.nasa.jpf.jvm.ClassFile;
import gov.nasa.jpf.report.Publisher;
import gov.nasa.jpf.report.PublisherExtension;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.search.SearchListener;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.FieldInfo;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.VMListener;
import gov.nasa.jpf.vm.StackFrame;
import gov.nasa.jpf.ListenerAdapter;

import gov.nasa.jpf.jvm.bytecode.INVOKESTATIC;
import gov.nasa.jpf.jvm.bytecode.IRETURN;
import gov.nasa.jpf.vm.bytecode.ReturnInstruction;
import gov.nasa.jpf.ListenerAdapter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.Objects;

public class MemoizationListener extends ListenerAdapter {
    // Cache to store method calls and their results
    private Map<MethodCall, Object> memoizationCache = new HashMap<>();

    // Map to track ongoing method calls per thread
    private Map<Integer, MethodCall> methodCallMap = new HashMap<>();

    // Map to track ongoing non-memoizable method calls per thread
    private Map<Integer, MethodCall> nonMemoizableCallMap = new HashMap<>();

    @Override
    public void executeInstruction(VM vm, ThreadInfo ti, Instruction insn) {
        // Check if the instruction is a static method invocation
        if (insn instanceof INVOKESTATIC) {
            INVOKESTATIC invokeInsn = (INVOKESTATIC) insn;
            MethodInfo mi = invokeInsn.getInvokedMethod();

            String className = mi.getClassName();

            if (!className.endsWith("Support")) {
                return;
            }

            // Check if the method is memoizable
            if (isMemoizable(mi)) {
                Object[] args = invokeInsn.getArgumentValues(ti);
                Object[] processedArgs = processArguments(ti, mi, args);
                MethodCall methodCall = new MethodCall(mi, processedArgs);

                if (memoizationCache.containsKey(methodCall)) {
                    Object cachedResult = memoizationCache.get(methodCall);
                    System.out.println("Returning memoized return value for " + methodCall + ":" + cachedResult + ".");

                    // Skip method execution and set return value
                    ti.getModifiableTopFrame().clearOperandStack();
                    pushReturnValue(ti, mi, cachedResult);
                    
                    // Set the next instruction to skip method execution
                    ti.skipInstruction(insn.getNext());
                } else {
                    // Store the method call for use after execution
                    methodCallMap.put(ti.getId(), methodCall);
                }
            } else {
                Object[] args = invokeInsn.getArgumentValues(ti);
                MethodCall methodCall = new MethodCall(mi, args);
                nonMemoizableCallMap.put(ti.getId(), methodCall);
            }
        }
    }

    @Override
    public void instructionExecuted(VM vm, ThreadInfo ti, Instruction nextInsn, Instruction executedInsn) {
        // After method execution, store the result
        if (executedInsn instanceof ReturnInstruction) {
            ReturnInstruction returnInsn = (ReturnInstruction) executedInsn;
            MethodInfo mi = returnInsn.getMethodInfo();

            String className = mi.getClassName();

            if (!className.endsWith("Support")) {
                return;
            }

            if (isMemoizable(mi)) {
                MethodCall methodCall = methodCallMap.remove(ti.getId());

                Object result = getReturnValue(ti, returnInsn);
                memoizationCache.put(methodCall, result);

                System.out.println("Memoizing " + methodCall + ":" + result + ".");
            } else {
                // Handle non-memoizable methods
                MethodCall methodCall = nonMemoizableCallMap.remove(ti.getId());

                if (methodCall != null) {
                    Object result = getReturnValue(ti, returnInsn);
                    System.out.println(methodCall + ":" + result + " is not memoizable.");
                }
            }
        }
    }

    private boolean isMemoizable(MethodInfo mi) {
        // Check if method is static
        if (!mi.isStatic()) {
            return false;
        }

        // Check return type
        String returnType = mi.getReturnTypeName();
        if (!returnType.equals("int") && !returnType.equals("double")) {
            return false;
        }

        // All parameter types must be primitive or end with "Support"
        String[] paramTypes = mi.getArgumentTypeNames();
        for (String paramType : paramTypes) {
            if (isPrimitive(paramType)) {
                continue;
            }
            if (!paramType.endsWith("Support")) {
                return false;
            }
        }

        return true;
    }

    private boolean isPrimitive(String typeName) {
        switch (typeName) {
            case "int":
            case "double":
            case "boolean":
            case "char":
            case "byte":
            case "short":
            case "long":
            case "float":
                return true;
            default:
                return false;
        }
    }

    private Object[] processArguments(ThreadInfo ti, MethodInfo mi, Object[] args) {
        String[] paramTypes = mi.getArgumentTypeNames();
        Object[] processedArgs = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            String paramType = paramTypes[i];
            if (isPrimitive(paramType)) {
                processedArgs[i] = args[i];
            } else {
                ElementInfo ei = (ElementInfo) args[i];
                String objectState = getObjectState(ti, ei, new HashSet<>());
                processedArgs[i] = objectState;
            }
        }

        return processedArgs;
    }

    private String getObjectState(ThreadInfo ti, ElementInfo ei, Set<Integer> visited) {
        if (ei == null || visited.contains(ei.getObjectRef())) {
            return "null";
        }

        visited.add(ei.getObjectRef());

        ClassInfo ci = ei.getClassInfo();
        String className = ci.getName();

        // Only process classes ending with "Support"
        if (!className.endsWith("Support")) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(className).append("{");

        // Iterate over fields
        for (FieldInfo fi : ci.getDeclaredInstanceFields()) {
            String fname = fi.getName();
            String ftype = fi.getType();

            if (isPrimitive(ftype)) {
                Object value = ei.getFieldValueObject(fname);
                sb.append(fname).append("=").append(value).append(";");
            } else {
                int ref = ei.getReferenceField(fname);
                ElementInfo fei = ti.getElementInfo(ref);
                String fieldState = getObjectState(ti, fei, visited);
                if (!fieldState.isEmpty()) {
                    sb.append(fname).append("=").append(fieldState).append(";");
                }
            }
        }

        sb.append("}");
        return sb.toString();
    }

    private void pushReturnValue(ThreadInfo ti, MethodInfo mi, Object value) {
        StackFrame frame = ti.getModifiableTopFrame();
        String returnType = mi.getReturnTypeName();

        if (returnType.equals("int")) {
            frame.push(((Number) value).intValue());
        } else if (returnType.equals("double")) {
            frame.pushLong(Double.doubleToLongBits(((Number) value).doubleValue()));
        }
    }

    private Object getReturnValue(ThreadInfo ti, ReturnInstruction returnInsn) {
        StackFrame frame = ti.getTopFrame();
        String returnType = returnInsn.getMethodInfo().getReturnTypeName();

        if (returnType.equals("int")) {
            return frame.pop();
        } else if (returnType.equals("double")) {
            return frame.popDouble();
        }
        return returnType;
    }

    // Represent a method call
    private static class MethodCall {
        private final String methodName;
        private final Object[] arguments;

        public MethodCall(MethodInfo mi, Object[] args) {
            this.methodName = mi.getName();
            this.arguments = args.clone();
        }

        @Override
        public int hashCode() {
            return Objects.hash(methodName, Arrays.deepHashCode(arguments));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof MethodCall)) return false;
            MethodCall other = (MethodCall) obj;
            return methodName.equals(other.methodName) &&
                   Arrays.deepEquals(arguments, other.arguments);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(methodName);
            sb.append("(");
            for (int i = 0; i < arguments.length; i++) {
                sb.append(arguments[i]);
                if (i < arguments.length - 1) sb.append(", ");
            }
            sb.append(")");
            return sb.toString();
        }
    }

}