package org.jacoco.gosu;

import org.jacoco.core.internal.analysis.filter.IFilter;
import org.jacoco.core.internal.analysis.filter.IFilterContext;
import org.jacoco.core.internal.analysis.filter.IFilterOutput;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Filters out Gosu compiler-generated null-safety checks.
 *
 * <p>Gosu generates two types of null-safety patterns:
 *
 * <p><b>Pattern 1: Null-safe navigation</b> (returns null if null)
 * <pre>
 * aload X
 * ifnonnull labelA
 * aconst_null
 * checkcast Type
 * goto labelB
 * labelA:
 *   aload X
 *   invokevirtual/invokeinterface Type.method()
 * labelB:
 * </pre>
 *
 * <p><b>Pattern 2: Defensive null check</b> (throws NPE if null)
 * <pre>
 * aload X
 * ifnonnull label
 * new java/lang/NullPointerException
 * dup
 * invokespecial java/lang/NullPointerException.&lt;init&gt;()V
 * athrow
 * label:
 * </pre>
 */
public final class GosuNullSafetyFilter implements IFilter {

    private static final String LOG_PREFIX = "[GosuNullSafetyFilter]";
    private static final boolean DEBUG = Boolean.getBoolean("jacoco.gosu.filter.debug");

    @Override
    public void filter(final MethodNode methodNode, final IFilterContext context,
            final IFilterOutput output) {
        if (methodNode.instructions.size() == 0) {
            return;
        }

        AbstractInsnNode current = methodNode.instructions.getFirst();
        while (current != null) {
            // Try each pattern detector - if one matches, skip to the next instruction after it
            AbstractInsnNode next = filterNullSafeNavigation(current, methodNode, output);
            if (next != current) {
                current = next;
                continue;
            }

            next = filterDefensiveNullCheck(current, methodNode, output);
            if (next != current) {
                current = next;
                continue;
            }

            next = filterSimplifiedNullSafePattern(current, methodNode, output);
            if (next != current) {
                current = next;
                continue;
            }

            next = filterBooleanNullSafePattern(current, methodNode, output);
            if (next != current) {
                current = next;
                continue;
            }

            next = filterArrayNullSafePattern(current, methodNode, output);
            if (next != current) {
                current = next;
                continue;
            }

            current = current.getNext();
        }
    }

    /**
     * Filters Pattern 1: Null-safe navigation
     *
     * Matches: aload X -> ifnonnull -> aconst_null -> checkcast -> goto
     */
    private AbstractInsnNode filterNullSafeNavigation(final AbstractInsnNode start,
            final MethodNode method, final IFilterOutput output) {
        if (start == null || start.getOpcode() != Opcodes.ALOAD) {
            return start;
        }
        final VarInsnNode aload1 = (VarInsnNode) start;

        // Next: ifnonnull labelA
        AbstractInsnNode next = start.getNext();
        if (next == null || next.getOpcode() != Opcodes.IFNONNULL) {
            return start;
        }
        final JumpInsnNode ifnonnull = (JumpInsnNode) next;
        final LabelNode labelA = ifnonnull.label;

        // Next: aconst_null
        next = next.getNext();
        if (next == null || next.getOpcode() != Opcodes.ACONST_NULL) {
            return start;
        }

        // Next: checkcast Type
        next = next.getNext();
        if (next == null || next.getOpcode() != Opcodes.CHECKCAST) {
            return start;
        }
        final TypeInsnNode checkcast = (TypeInsnNode) next;

        // Next: goto labelB (optional, might fall through)
        AbstractInsnNode gotoInsn = next.getNext();
        if (gotoInsn != null && gotoInsn.getOpcode() == Opcodes.GOTO) {
            next = gotoInsn;
        }

        // Next should be labelA
        AbstractInsnNode labelANode = next.getNext();
        if (labelANode == null || labelANode != labelA) {
            return start;
        }

        // After labelA: aload X
        AbstractInsnNode aload2Node = labelANode.getNext();
        if (aload2Node == null || aload2Node.getOpcode() != Opcodes.ALOAD) {
            return start;
        }
        final VarInsnNode aload2 = (VarInsnNode) aload2Node;

        // Must be same variable
        if (aload1.var != aload2.var) {
            return start;
        }

        // Next: invokevirtual/invokeinterface/invokestatic
        AbstractInsnNode invoke = aload2Node.getNext();
        if (invoke == null) {
            return start;
        }
        final int invokeOp = invoke.getOpcode();
        if (invokeOp != Opcodes.INVOKEVIRTUAL && invokeOp != Opcodes.INVOKEINTERFACE
                && invokeOp != Opcodes.INVOKESTATIC) {
            return start;
        }

        // Pattern matched! Ignore this branch
        if (DEBUG) {
            String invokeType = invokeOp == Opcodes.INVOKEVIRTUAL ? "INVOKEVIRTUAL"
                    : (invokeOp == Opcodes.INVOKEINTERFACE ? "INVOKEINTERFACE" : "INVOKESTATIC");
            System.out.println(LOG_PREFIX + " PATTERN 1 (Null-safe navigation) | Method: "
                    + method.name + method.desc + " | Var: " + aload1.var + " | Cast: "
                    + checkcast.desc + " | Invoke: " + invokeType);
        }

        output.ignore(start, ifnonnull);
        output.ignore(ifnonnull.label, invoke);

        return invoke.getNext();
    }

    /**
     * Filters Pattern 2: Defensive null check with NPE throw
     *
     * Matches: aload X -> ifnonnull -> new NPE -> dup -> invokespecial init -> athrow
     */
    private AbstractInsnNode filterDefensiveNullCheck(final AbstractInsnNode start,
            final MethodNode method, final IFilterOutput output) {
        if (start == null || start.getOpcode() != Opcodes.ALOAD) {
            return start;
        }
        final VarInsnNode aloadVar = (VarInsnNode) start;

        // Next: ifnonnull label
        AbstractInsnNode next = start.getNext();
        if (next == null || next.getOpcode() != Opcodes.IFNONNULL) {
            return start;
        }
        final JumpInsnNode ifnonnull = (JumpInsnNode) next;
        final LabelNode successLabel = ifnonnull.label;

        // Look ahead for NPE pattern (up to 5 instructions)
        AbstractInsnNode current = next;
        boolean foundNew = false;
        boolean foundDup = false;
        boolean foundInit = false;
        boolean foundAthrow = false;

        for (int i = 0; i < 5 && current != null; i++) {
            switch (current.getOpcode()) {
                case Opcodes.NEW:
                    if (current instanceof TypeInsnNode) {
                        TypeInsnNode typeNode = (TypeInsnNode) current;
                        if ("java/lang/NullPointerException".equals(typeNode.desc)) {
                            foundNew = true;
                        }
                    }
                    break;
                case Opcodes.DUP:
                    foundDup = true;
                    break;
                case Opcodes.INVOKESPECIAL:
                    if (current instanceof MethodInsnNode) {
                        MethodInsnNode methodNode = (MethodInsnNode) current;
                        if ("<init>".equals(methodNode.name)
                                && "java/lang/NullPointerException".equals(methodNode.owner)) {
                            foundInit = true;
                        }
                    }
                    break;
                case Opcodes.ATHROW:
                    foundAthrow = true;
                    break;
            }
            current = current.getNext();
        }

        // If we found the NPE pattern and the next is the success label, match it
        if (foundNew && foundDup && foundInit && foundAthrow && current == successLabel) {
            if (DEBUG) {
                System.out.println(LOG_PREFIX + " PATTERN 2 (Defensive null check - throws NPE) | Method: "
                            + method.name + method.desc + " | Var: " + aloadVar.var);
            }
            output.ignore(start, ifnonnull);
            output.ignore(ifnonnull.label, current);
            return current.getNext();
        }

        return start;
    }

    /**
     * Filters Pattern 3: Simplified null-safe patterns (aload -> ifnonnull -> aconst_null -> goto/areturn)
     */
    private AbstractInsnNode filterSimplifiedNullSafePattern(final AbstractInsnNode start,
            final MethodNode method, final IFilterOutput output) {
        if (start == null || start.getOpcode() != Opcodes.ALOAD) {
            return start;
        }
        final VarInsnNode aload = (VarInsnNode) start;

        // Next: ifnonnull label
        AbstractInsnNode next = start.getNext();
        if (next == null || next.getOpcode() != Opcodes.IFNONNULL) {
            return start;
        }
        final JumpInsnNode ifnonnull = (JumpInsnNode) next;

        // Next: aconst_null
        next = next.getNext();
        if (next == null || next.getOpcode() != Opcodes.ACONST_NULL) {
            return start;
        }

        // Next: checkcast, goto, or areturn
        next = next.getNext();
        if (next == null) {
            return start;
        }

        int nextOp = next.getOpcode();
        if (nextOp != Opcodes.CHECKCAST && nextOp != Opcodes.GOTO && nextOp != Opcodes.ARETURN) {
            return start;
        }

        // Pattern matched! Ignore this branch
        if (DEBUG) {
            String patternType = nextOp == Opcodes.ARETURN ? "null-return" : "simplified null-safe";
            System.out.println(LOG_PREFIX + " PATTERN 3 (" + patternType + ") | Method: "
                        + method.name + method.desc + " | Var: " + aload.var);
        }

        output.ignore(start, ifnonnull);
        output.ignore(ifnonnull.label, next);

        return next.getNext();
    }

    /**
     * Filters Pattern 4: Boolean null-safe patterns (?.HasElements)
     *
     * Matches: aload -> ifnonnull -> iconst_0/iconst_1 -> goto
     */
    private AbstractInsnNode filterBooleanNullSafePattern(final AbstractInsnNode start,
            final MethodNode method, final IFilterOutput output) {
        if (start == null || start.getOpcode() != Opcodes.ALOAD) {
            return start;
        }
        final VarInsnNode aload = (VarInsnNode) start;

        // Next: ifnonnull label
        AbstractInsnNode next = start.getNext();
        if (next == null || next.getOpcode() != Opcodes.IFNONNULL) {
            return start;
        }
        final JumpInsnNode ifnonnull = (JumpInsnNode) next;

        // Next: iconst_0 or iconst_1
        next = next.getNext();
        if (next == null || (next.getOpcode() != Opcodes.ICONST_0 && next.getOpcode() != Opcodes.ICONST_1)) {
            return start;
        }
        final int constValue = next.getOpcode() == Opcodes.ICONST_0 ? 0 : 1;

        // Next: goto
        next = next.getNext();
        if (next == null || next.getOpcode() != Opcodes.GOTO) {
            return start;
        }

        // Pattern matched! Ignore this branch
        if (DEBUG) {
            System.out.println(LOG_PREFIX + " PATTERN 4 (Boolean null-safe) | Method: "
                        + method.name + method.desc + " | Var: " + aload.var + " | Const: " + constValue);
        }

        output.ignore(start, ifnonnull);
        output.ignore(ifnonnull.label, next.getNext());

        return next.getNext().getNext();
    }

    /**
     * Filters Pattern 5: Array creation null-safe patterns
     *
     * Matches: aload -> ifnonnull -> iconst_0/anewarray -> checkcast -> goto
     */
    private AbstractInsnNode filterArrayNullSafePattern(final AbstractInsnNode start,
            final MethodNode method, final IFilterOutput output) {
        if (start == null || start.getOpcode() != Opcodes.ALOAD) {
            return start;
        }
        final VarInsnNode aload = (VarInsnNode) start;

        // Next: ifnonnull label
        AbstractInsnNode next = start.getNext();
        if (next == null || next.getOpcode() != Opcodes.IFNONNULL) {
            return start;
        }
        final JumpInsnNode ifnonnull = (JumpInsnNode) next;

        // Next: iconst_0 or anewarray
        next = next.getNext();
        if (next == null) {
            return start;
        }

        int arrayOp = next.getOpcode();
        if (arrayOp != Opcodes.ICONST_0 && arrayOp != Opcodes.ANEWARRAY) {
            return start;
        }

        // For ANEWARRAY, check the type
        if (arrayOp == Opcodes.ANEWARRAY) {
            if (!(next instanceof TypeInsnNode)) {
                return start;
            }
            // Could check for specific types here if needed
        }

        // Next: checkcast (optional) or goto
        next = next.getNext();
        if (next != null) {
            if (next.getOpcode() == Opcodes.CHECKCAST) {
                next = next.getNext(); // skip checkcast
            }
            if (next != null && next.getOpcode() == Opcodes.GOTO) {
                // Pattern matched!
                if (DEBUG) {
                    System.out.println(LOG_PREFIX + " PATTERN 5 (Array null-safe) | Method: "
                                + method.name + method.desc + " | Var: " + aload.var);
                }
                output.ignore(start, ifnonnull);
                output.ignore(ifnonnull.label, next);
                return next.getNext();
            }
        }

        return start;
    }
}
