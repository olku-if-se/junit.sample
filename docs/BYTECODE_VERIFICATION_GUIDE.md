# Gosu Bytecode Pattern Verification Guide

Based on JaCoCo PR #1905's Kotlin verification approach, here's how to verify Gosu null-safe patterns using **javap**.

## Tool Setup

The standard Java bytecode disassembler `javap` is included with every JDK installation.

```bash
# Verify javap is available
javap -version

# Expected output: similar to "javap 11.0.x" or higher
```

## Step 1: Locate Compiled Classes

After building, find the compiled enhancement class:

```bash
find build -name "PolicyPeriodEnhancement.class" -type f
```

Expected location:
```
build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class
```

## Step 2: Disassemble Bytecode with Verbose Output

Use `javap -v -p` to get complete bytecode inspection:

```bash
javap -v -p build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class
```

Parameters explained:
- `-v` : Verbose output (shows bytecode, line numbers, local vars, exceptions)
- `-p` : Show private members
- `-c` : Disassemble code (included in verbose)

## Step 3: What to Look For

### Pattern 1: Null-Safe Navigation

In the output, search for sequences like:

```bytecode
aload_<N>              // Load variable
ifnonnull <label>      // If not null, jump to method call
aconst_null           // Push null constant
checkcast <Type>      // Type check on null value
goto <label2>         // Skip the actual method call
<label>:              // Label where method call happens
  aload_<N>
  invokevirtual ... // Actual method invocation
<label2>:             // Continue after both branches
```

### Pattern 2: Defensive Null Check (NPE Throw)

Search for sequences like:

```bytecode
aload_<N>             // Load variable
ifnonnull <label>     // If not null, jump to success
new java/lang/NullPointerException    // Create NPE
 dup                   // Duplicate on stack
invokespecial <init>  // Initialize NPE
athrow               // Throw exception
<label>:             // Success path
```

## Step 4: Example Inspection Command

```bash
#!/bin/bash
echo "=== Analyzing PolicyPeriodEnhancement Bytecode ==="
javap -v -p build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class | \
    grep -A 50 "public static java.util.Date getFirstPeriodInTermCreateTime_Ext"
```

## Step 5: What JaCoCo Filter Does

The filter detects these exact bytecode sequences using ASM (bytecode manipulation library):

```java
// Pattern detection in GosuNullSafetyFilter.java
aload1.getOpcode() == Opcodes.ALOAD        // Check ALOAD
next.getOpcode() == Opcodes.IFNONNULL      // Check IFNONNULL
next.getOpcode() == Opcodes.ACONST_NULL    // Check ACONST_NULL
next.getOpcode() == Opcodes.CHECKCAST      // Check CHECKCAST
invoke.getOpcode() == Opcodes.INVOKEVIRTUAL  // Check INVOKEVIRTUAL

// When all match, mark ranges as ignorable:
output.ignore(start, ifnonnull);           // Ignore null-check
output.ignore(ifnonnull.label, invoke);    // Ignore method call
```

## Expected Bytecode Output Example

For line 13: `return this.FirstPeriodInTerm?.CreateTime`

You should see in the `getFirstPeriodInTermCreateTime_Ext` method:

```
Code:
  stack=1, locals=2, args_size=1
     0: aload_0              // Load 'this' (parameter $that$)
     1: astore_1             // Store in variable 1
     2: aload_1              // Load variable 1
     3: ifnonnull     #13   // If not null, jump to label 13
     6: aconst_null          // Push null
     7: checkcast     #2     // type: java/util/Date
    10: goto          #15   // Jump to label 15
    13: aload_1              // Load variable 1 (non-null path)
    14: invokevirtual #3     // Call getCreateTime()Ljava/util/Date;
    17: areturn              // Return result
```

**This is Pattern 1** - null-safe navigation with 2 branches to ignore:
1. Instructions 0-3: Loading and null-check (aload → ifnonnull)
2. Instructions 13-14: Method call path (label → invokevirtual)

## Full Verification Script

```bash
#!/bin/bash

echo "=================================="
echo "GOSU BYTECODE PATTERN VERIFICATION"
echo "=================================="
echo ""

ENHANCEMENT_CLASS="build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class"

if [ ! -f "$ENHANCEMENT_CLASS" ]; then
    echo "❌ Class not found: $ENHANCEMENT_CLASS"
    echo "   Run: ./gradlew clean test compileGosu"
    exit 1
fi

echo "✓ Class found at: $ENHANCEMENT_CLASS"
echo ""

echo "=== Method 1: getFirstPeriodInTermCreateTime_Ext ==="
echo "Expected: 2 pattern matches (null-safe navigation)"
echo ""
javap -v -p "$ENHANCEMENT_CLASS" 2>/dev/null | \
    sed -n '/public static java.util.Date getFirstPeriodInTermCreateTime_Ext/,/public static/p' | \
    head -30

echo ""
echo "=== Method 2: getAvailableBrandConceptsForProdCode ==="
echo "Expected: Multiple pattern matches (complex collection iteration)"
echo ""
javap -v -p "$ENHANCEMENT_CLASS" 2>/dev/null | \
    sed -n '/public static java.util.List getAvailableBrandConceptsForProdCode/,/public static/p' | \
    head -50

echo ""
echo "=== Method 3: getFirstPeriodProducerCodeName ==="
echo "Expected: Multiple pattern matches (5-step null-safe chain)"
echo ""
javap -v -p "$ENHANCEMENT_CLASS" 2>/dev/null | \
    sed -n '/public static java.lang.String getFirstPeriodProducerCodeName/,/public static/p' | \
    head -40

echo ""
echo "=== Summary ==="
echo "Count ALOAD → IFNONNULL → ACONST_NULL → CHECKCAST sequences"
echo "Count ALOAD → IFNONNULL → NEW → ATHROW sequences"
echo "These are the patterns the JaCoCo filter detects and ignores"
echo ""
```

## How to Verify Filter is Working

1. **Before filtering**: Count ALOAD/IFNONNULL sequences (raw bytecode)
2. **After filtering**: Filter reports in logs show how many were detected
3. **In HTML report**: Branch counts are reduced vs. raw bytecode

Example verification:

```bash
# Step 1: Count raw patterns in bytecode
echo "Raw bytecode patterns:"
javap -v -p $CLASS | grep -c "ifnonnull"    # Count null-checks
javap -v -p $CLASS | grep -c "aconst_null"  # Count null pushes

# Step 2: Run tests and observe filter logs
./gradlew test 2>&1 | grep -E "\[Gosu.*Filter\]"

# Step 3: Check JaCoCo report
cat build/reports/jacoco/test/jacocoTestReport.csv | \
    grep "PolicyPeriodEnhancement" | \
    awk -F',' '{print $3 " | Branches: " $9+$10}'
```

## Key Metrics for Verification

| Bytecode Element | Filter Action | Verification |
|-----------------|---------------|--------------  |
| `aload X` | Check instruction | Should appear before `ifnonnull` |
| `ifnonnull LABEL` | Detect + Ignore | Log message shows pattern detected |
| `aconst_null` | Part of pattern | Should follow `ifnonnull` |
| `checkcast Type` | Part of pattern | Type info logged (e.g., Ljava/util/Date;) |
| `invokevirtual` | Mark boundary | Should mark end of pattern |

## References

- JaCoCo PR #1905: https://github.com/jacoco/jacoco/pull/1905 (Kotlin filter)
- JCoder Tool: https://github.com/alblue/bytecode-viewer (Visual bytecode tool)
- javap Manual: `javap -help`
- ASM Opcode Reference: https://asm.ow2.io/

## Quick Test

To quickly verify patterns exist in compiled class:

```bash
# Show all method signatures with their bytecode
javap -c build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class | head -100

# Count specific instructions
javap -c build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class | \
    grep -E "aload|ifnonnull|aconst_null|checkcast" | wc -l

# Look for null-safe patterns
javap -v build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class | \
    grep -B1 -A3 "ifnonnull"
```
