/*******************************************************************************
 * Copyright (c) 2009, 2025 Mountainminds GmbH & Co. KG and Contributors
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Original GosuNullSafetyFilter implementation - Oleksandr Kucherenko (kucherenko.alex[at]gmail.com)
 *
 *******************************************************************************/
package org.jacoco.core.internal.analysis.filter;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Filters out Gosu compiler-generated null-safety checks.
 *
 * <p>
 * Gosu generates null-safety patterns for null-safe navigation and defensive
 * null checks. This filter identifies and excludes these compiler-generated
 * branches from coverage analysis.
 */
public final class GosuNullSafetyFilter implements IFilter {

    public void filter(final MethodNode methodNode,
                       final IFilterContext context, final IFilterOutput output) {
        final Matcher matcher = new Matcher();
        for (final AbstractInsnNode i : methodNode.instructions) {
            matcher.match(i, output);
        }
    }

    /**
     * Represents a single step in a bytecode pattern matching sequence.
     */
    private interface PatternStep {
        /**
         * Attempts to match this step against the current cursor position.
         *
         * @param matcher the matcher with current cursor
         * @return true if the step matches and cursor advanced, false otherwise
         */
        boolean match(AbstractMatcher matcher);
    }

    /**
     * Holder for capturing instruction nodes during pattern matching.
     */
    private static class Capture {
        AbstractInsnNode node;
    }

    /**
     * Matches an exact opcode.
     */
    private static class OpcodeStep implements PatternStep {
        private final int opcode;
        private final Capture capture;

        OpcodeStep(final int opcode) {
            this(opcode, null);
        }

        OpcodeStep(final int opcode, final Capture capture) {
            this.opcode = opcode;
            this.capture = capture;
        }

        public boolean match(final AbstractMatcher matcher) {
            if (!matcher.safeNextIs(opcode))
                return false;
            if (capture != null) {
                capture.node = matcher.cursor;
            }
            return true;
        }
    }

    /**
     * Matches one of multiple opcodes.
     */
    private static class OneOfStep implements PatternStep {
        private final int[] opcodes;

        OneOfStep(final int... opcodes) {
            this.opcodes = opcodes;
        }

        public boolean match(final AbstractMatcher matcher) {
            if (!matcher.safeNext())
                return false;
            final int op = matcher.cursor.getOpcode();
            for (final int expected : opcodes) {
                if (op == expected)
                    return true;
            }
            return false;
        }
    }

    /**
     * Optionally matches and skips an opcode if present.
     */
    private static class OptionalOpcodeStep implements PatternStep {
        private final int opcode;

        OptionalOpcodeStep(final int opcode) {
            this.opcode = opcode;
        }

        public boolean match(final AbstractMatcher matcher) {
            // Try to advance
            if (!matcher.safeNext())
                return false;
            // If current matches the optional opcode, advance past it
            if (matcher.cursor.getOpcode() == opcode) {
                return matcher.safeNext();
            }
            // Otherwise, stay at current position (don't advance further)
            return true;
        }
    }

    /**
     * Matches a type instruction (NEW, CHECKCAST, etc).
     */
    private static class TypeStep implements PatternStep {
        private final int opcode;
        private final String desc;

        TypeStep(final int opcode, final String desc) {
            this.opcode = opcode;
            this.desc = desc;
        }

        public boolean match(final AbstractMatcher matcher) {
            return matcher.safeNextIsType(opcode, desc);
        }
    }

    /**
     * Matches a method invoke instruction.
     */
    private static class InvokeStep implements PatternStep {
        private final int opcode;
        private final String owner;
        private final String name;
        private final String descriptor;

        InvokeStep(final int opcode, final String owner, final String name,
                   final String descriptor) {
            this.opcode = opcode;
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }

        public boolean match(final AbstractMatcher matcher) {
            return matcher.safeNextIsInvoke(opcode, owner, name, descriptor);
        }
    }

    /**
     * Verifies that cursor reaches the label from a captured JumpInsnNode.
     */
    private static class LabelMatchStep implements PatternStep {
        private final Capture jumpCapture;

        LabelMatchStep(final Capture jumpCapture) {
            this.jumpCapture = jumpCapture;
        }

        public boolean match(final AbstractMatcher matcher) {
            if (jumpCapture.node == null)
                return false;
            final JumpInsnNode jump = (JumpInsnNode) jumpCapture.node;
            if (jump.label == null)
                return false;
            if (!matcher.safeNext())
                return false;
            return matcher.cursor == jump.label;
        }
    }

    /**
     * Gosu Language null-safety matcher that verify 5 different safety patterns
     * generated by Gosu.
     */
    private static class Matcher extends AbstractMatcher {

        /**
         * Quick match - expected ALOAD, otherwise exit.
         */
        public void match(final AbstractInsnNode start,
                          final IFilterOutput output) {
            // first step of all matches: aload X -> ...
            if (start.getOpcode() != org.objectweb.asm.Opcodes.ALOAD) {
                return;
            }
            cursor = start;
            final VarInsnNode var = (VarInsnNode) start;

            // Try to match Gosu null-safety patterns
            if (matchNullSafeNavigation(var, output)) {
                return;
            }

            if (matchDefensiveNullCheck(var, output)) {
                return;
            }

            if (matchSimplifiedNullSafe(var, output)) {
                return;
            }

            if (matchBooleanNullSafe(var, output)) {
                return;
            }

            if (matchArrayNullSafe(var, output)) {
                return;
            }
        }

        /**
         * Generic pattern matcher that validates a sequence of pattern steps.
         *
         * @param steps the pattern steps to match
         * @return true if all steps match
         */
        private boolean matchPattern(final PatternStep[] steps) {
            for (final PatternStep step : steps) {
                if (!step.match(this)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Pattern: aload X -> ifnonnull labelA -> aconst_null -> checkcast ->
         * goto labelB -> labelA
         */
        private boolean matchNullSafeNavigation(final VarInsnNode var,
                                                final IFilterOutput output) {
            final Capture ifnonnullCapture = new Capture();

            final PatternStep[] pattern = new PatternStep[]{
                    new OpcodeStep(org.objectweb.asm.Opcodes.IFNONNULL,
                            ifnonnullCapture),
                    new OpcodeStep(org.objectweb.asm.Opcodes.ACONST_NULL),
                    new OpcodeStep(org.objectweb.asm.Opcodes.CHECKCAST),
                    new OpcodeStep(org.objectweb.asm.Opcodes.GOTO),
                    new LabelMatchStep(ifnonnullCapture)};

            if (matchPattern(pattern)) {
                final JumpInsnNode ifnonnull = (JumpInsnNode) ifnonnullCapture.node;

                // Pattern matched - ignore the null-safety check
                output.ignore(var, ifnonnull);
                output.ignore(ifnonnull.label, cursor);
                return true;
            }

            return false;
        }

        /**
         * Pattern: aload X -> ifnonnull label -> new NPE -> dup -> init ->
         * athrow -> label
         */
        private boolean matchDefensiveNullCheck(final VarInsnNode var,
                                                final IFilterOutput output) {
            final Capture ifnonnullCapture = new Capture();

            final PatternStep[] pattern = new PatternStep[]{
                    new OpcodeStep(org.objectweb.asm.Opcodes.IFNONNULL,
                            ifnonnullCapture),
                    new TypeStep(org.objectweb.asm.Opcodes.NEW,
                            "java/lang/NullPointerException"),
                    new OpcodeStep(org.objectweb.asm.Opcodes.DUP),
                    new InvokeStep(org.objectweb.asm.Opcodes.INVOKESPECIAL,
                            "java/lang/NullPointerException", "<init>", "()V"),
                    new OpcodeStep(org.objectweb.asm.Opcodes.ATHROW),
                    new LabelMatchStep(ifnonnullCapture)};

            if (matchPattern(pattern)) {
                final JumpInsnNode ifnonnull = (JumpInsnNode) ifnonnullCapture.node;

                // Pattern matched - ignore the null-safety check
                output.ignore(var, ifnonnull);
                output.ignore(ifnonnull.label, cursor);
                return true;
            }

            return false;
        }

        /**
         * Pattern: aload X -> ifnonnull label -> aconst_null -> areturn ->
         * label
         */
        private boolean matchSimplifiedNullSafe(final VarInsnNode var,
                                                final IFilterOutput output) {
            final Capture ifnonnullCapture = new Capture();

            final PatternStep[] pattern = new PatternStep[]{
                    new OpcodeStep(org.objectweb.asm.Opcodes.IFNONNULL,
                            ifnonnullCapture),
                    new OpcodeStep(org.objectweb.asm.Opcodes.ACONST_NULL),
                    new OpcodeStep(org.objectweb.asm.Opcodes.ARETURN),
                    new LabelMatchStep(ifnonnullCapture)};

            if (matchPattern(pattern)) {
                final JumpInsnNode ifnonnull = (JumpInsnNode) ifnonnullCapture.node;

                // Pattern matched - ignore the null-safety check
                output.ignore(var, ifnonnull);
                output.ignore(ifnonnull.label, cursor);
                return true;
            }

            return false;
        }

        /**
         * Pattern: aload X -> ifnonnull label -> iconst_0/iconst_1 -> goto ->
         * label
         */
        private boolean matchBooleanNullSafe(final VarInsnNode var,
                                             final IFilterOutput output) {
            final Capture ifnonnullCapture = new Capture();

            final PatternStep[] pattern = new PatternStep[]{
                    new OpcodeStep(org.objectweb.asm.Opcodes.IFNONNULL,
                            ifnonnullCapture),
                    new OneOfStep(org.objectweb.asm.Opcodes.ICONST_0,
                            org.objectweb.asm.Opcodes.ICONST_1),
                    new OpcodeStep(org.objectweb.asm.Opcodes.GOTO),
                    new LabelMatchStep(ifnonnullCapture)};

            if (matchPattern(pattern)) {
                final JumpInsnNode ifnonnull = (JumpInsnNode) ifnonnullCapture.node;

                // Pattern matched - ignore the null-safety check
                output.ignore(var, ifnonnull);
                output.ignore(ifnonnull.label, cursor);
                return true;
            }

            return false;
        }

        /**
         * Pattern: aload X -> ifnonnull label -> iconst_0/anewarray ->
         * [checkcast] -> goto -> label
         */
        private boolean matchArrayNullSafe(final VarInsnNode var,
                                           final IFilterOutput output) {
            final Capture ifnonnullCapture = new Capture();

            final PatternStep[] pattern = new PatternStep[]{
                    new OpcodeStep(org.objectweb.asm.Opcodes.IFNONNULL,
                            ifnonnullCapture),
                    new OneOfStep(org.objectweb.asm.Opcodes.ICONST_0,
                            org.objectweb.asm.Opcodes.ANEWARRAY),
                    new OptionalOpcodeStep(org.objectweb.asm.Opcodes.CHECKCAST),
                    new OpcodeStep(org.objectweb.asm.Opcodes.GOTO),
                    new LabelMatchStep(ifnonnullCapture)};

            if (matchPattern(pattern)) {
                final JumpInsnNode ifnonnull = (JumpInsnNode) ifnonnullCapture.node;

                // Pattern matched - ignore the null-safety check
                output.ignore(var, ifnonnull);
                output.ignore(ifnonnull.label, cursor);
                return true;
            }

            return false;
        }
    }
}
