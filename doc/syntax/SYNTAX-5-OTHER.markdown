# Compiler directives

Mindcode allows you to alter some compiler options in the source code using special `#set` commands.
The basic syntax is: 

```
#set option = value
```

Some of these options can be alternatively specified as parameters of the command line compiler.

Supported compiler options are described below.

## Option `target`

Use the `target` option to specify the Mindcode version:

```
#set target = ML6
```

Possible values for this option are:
* `ML6`: compile for Mindcode Logic version 6
* `ML7S`: compile for Mindcode Logic version 7 standard processors
* `ML7W`: compile for Mindcode Logic version 7 world processor
* `ML7AS`: compile for Mindcode Logic version 7 (revision A) standard processors
* `ML7AW`: compile for Mindcode Logic version 7 (revision A) world processor

## Option `goal`

Use the `goal` option to specify whether Mindcode should prefer to generate smaller code, or faster code. 
Possible values are:

* `size`: Mindcode tries to generate smaller code.
* `speed`: Mindcode can generate additional instructions, if it makes the resulting code faster while adhering to 
  the 1000 instructions limit. When several possible optimizations of this kind are available, the ones having the 
  best effect (highest speedup per additional instruction generated) are selected until the instruction limit is 
  reached. 
* `auto`: the default value. At this moment the setting is identical to `speed`.

## Option `memory-model`

This option has been added to support future enhancements of Mindcode. Setting the option doesn't have any effect at 
this moment. 

## Option `passes`

Use the `passes` option to set the maximum number of optimization passes to be done:

```
#set passes = 10
```

The default value is 3 for the web application and 25 for the command line tool. The number of optimization passes 
can be limited to a value between 1 and 1000 (inclusive). 

A more complex code can usually benefit from more optimization passes. On the other hand, each optimization pass can 
take some time to complete. Limiting the total number can prevent optimization from taking too much time or 
consuming too many resources (this is a consideration for the web application).  

## Option `optimization`

Use the `optimization` option to set the optimization level of the compiler:

```
#set optimization = basic
```

Possible values for this option are:

* `off`
* `basic`
* `aggressive`

The `off` setting deactivates all optimizations. The `basic` setting performs most of the available optimizations.
The `aggressive` optimizations performs all the available optimizations, even those that might take more time, or 
which make changes that are potentially risky or make understanding of the resulting mlog code more difficult.

The default optimization level for the web application compiler is `basic`, for the command line compiler it is 
`aggressive`.

## Individual optimization options

It is possible to set the level of individual optimization tasks. Every optimization is assigned a name,
and this name can be used in the compiler directive like this:

```
#set dead-code-elimination = aggressive
```

Not all optimizations support the `aggressive` level. For those the level `aggressive` is the same as `basic`.
The complete list of available optimizations, including the option name for setting the level of given optimization
and availability of the aggressive optimization level is:

| Optimization                                                        | Option name                  | Aggressive |
|---------------------------------------------------------------------|------------------------------|:----------:|
| [Temporary Variables Elimination](#temporary-variables-elimination) | temp-variables-elimination   |     N      |
| [Case Expression Optimization](#case-expression-optimization)       | case-expression-optimization |     N      |
| [Dead Code Elimination](#dead-code-elimination)                     | dead-code-elimination        |     Y      |
| [Jump Normalization](#jump-normalization)                           | jump-normalization           |     N      |
| [Jump Optimization](#jump-optimization)                             | jump-optimization            |     N      |
| [Single Step Elimination](#single-step-elimination)                 | single-step-elimination      |     N      |
| [Expression Optimization](#expression-optimization)                 | expression-optimization      |     N      |
| [If Expression Optimization](#if-expression-optimization)           | if-expression-optimization   |     N      |
| [Data Flow Optimization](#data-flow-optimization)                   | data-flow-optimization       |     Y      |
| [Loop Optimization](#loop-optimization)                             | loop-optimization            |     Y      |
| [Loop Unrolling](#loop-unrolling)                                   | loop-unrolling               |     Y      |
| [Function Inlining](#function-inlining)                             | function-inlining            |     Y      |
| [Jump Straightening](#jump-straightening)                           | jump-straightening           |     N      |
| [Jump Threading](#jump-threading)                                   | jump-threading               |     Y      |
| [Unreachable Code Elimination](#unreachable-code-elimination)       | unreachable-code-elimination |     Y      |
| [Stack Optimization](#stack-optimization)                           | stack-optimization           |     N      |
| [Print Merging](#print-merging)                                     | print-merging                |     Y      |

You normally shouldn't need to deactivate any optimization, but if there was a bug in some of the optimizers,
deactivating it might allow you to use Mindcode until a fix is available. Partially activated optimizations
aren't routinely tested, so by deactivating one you might even discover some new bugs. On the other hand, full
optimization suite is tested by running compiled code on an emulated Mindustry processor, so bugs will hopefully
be rare. 

In particular, some optimizers expect to work on code that was already processed by different optimizations,
so turning off some optimizations might render other optimizations ineffective. **This is not a bug.**  

# Compiler optimization

Code optimization runs on compiled (ML) code. The compiled code is inspected for sequences of instructions
which can be removed or replaced by equivalent, but superior sequence of instructions. The new sequence might be 
smaller than the original one, or even larger than the original one, if it is executing faster
(see the [`goal` option](#option-goal)).  

The information on compiler optimizations is a bit technical. It might be useful if you're trying to better 
understand how Mindcode generates the ML code.

## Temporary Variables Elimination

The compiler sometimes creates temporary variables whose only function is to store output value of an instruction
before passing it somewhere else. This optimization removes all assignments from temporary variables that carry over
the output value of the preceding instruction. The `set` instruction is removed, while the preceding instruction is
updated to replace the temporary variable with the target variable used in the set statement.

The optimization is performed only when the following conditions are met:

1. The `set` instruction assigns from a temporary variable.
2. The temporary variable is used in exactly one other instruction. The other instruction
   immediately precedes the instruction producing the temporary variable.
3. All arguments of the other instruction referencing the temporary variable are output ones.

`push` and `pop` instructions are ignored by the above algorithm. `push`/`pop` instructions of any eliminated variables
are removed by the [Stack Optimization](#stack-optimization) down the line.

## Case Expression Optimization

Case expressions allocate temporary variable to hold the value of the input expression, even if the input expression
is actually a user defined variable. This optimization detects these instances, removes the temporary variable
and replaces it with the original variable containing the value of the input expression. The set instruction is
removed, while the other instructions are updated to replace the temporary variable with the one used in the set
statement.

The optimization is performed only when the following conditions are met:

1. The set instruction assigns to a case-expression temporary variable.
2. The set instruction is the first of all those using the temporary variable (the check is based on absolute
   instruction sequence in the program, not on the actual program flow).
3. Each subsequent instruction using the temporary variable conforms to the code generated by the compiler
   (i.e. has the form of `jump target <condition> __astX testValue`)

`push` and `pop` instructions are ignored by the above algorithm. `push`/`pop` instructions of any eliminated variables
are removed by the stack usage optimization down the line.

## Dead Code Elimination

This optimization inspects the entire code and removes all instructions that write to variables,
if none of the variables written to are actually read anywhere in the code.

This optimization support `basic` and `aggressive` levels of optimization. On the `aggressive` level,
the optimization removes all dead assignment, even assignments to unused global and main variables.

Dead Code Elimination also inspects your code and prints out suspicious variables:
* _Unused variables_: those are the variables that were, or could be, eliminated. On `basic` level,
  some unused variables might not be reported.
* _Uninitialized variables_: those are global variables that are read by the program, but never written to.
  (The [Data Flow Optimization](#data-flow-optimization) detects uninitialized local and function variables.)

Both cases deserve closer inspection, as they might be a result of a typo in a variable name.

## Jump Normalization

This optimization handles conditional jumps whose condition can be fully evaluated:

* always false conditional jumps are removed,
* always true conditional jumps are converted to unconditional ones.

A condition can be fully evaluated constant if both of its operands are literals, or if they're variables whose values 
were determined to be constant by the data flow analysis. 

The first case reduces the code size and speeds up execution. The second one in itself improves neither size nor speed,
but allows those jumps to be handled by other optimizations aimed at unconditional jumps.

## Jump Optimization

Conditional jumps are sometimes compiled into an `op` instruction evaluating a boolean expression, and a conditional
jump acting on the value of the expression.

This optimization turns the following sequence of instructions:

```
op <comparison> var A B
jump label equal/notEqual var false
```

into

```
jump label <inverse of comparison>/<comparison> A B
```

Prerequisites:

1. `jump` is an `equal`/`notEqual` comparison to `false`,
2. `var` is a temporary variable,
3. `var` is not used anywhere in the program except these two instructions,
4. `<comparison>` has an inverse/`<comparison>` exists as a condition.

## Single Step Elimination

The Mindcode compiler sometimes generates sequences of unconditional jumps where each jump targets the next instruction.
This optimization finds and removes all such jumps.

Technically, if we have a sequence
```
0: jump 2 ...
1: jump 2 ...
2: ...
```
we could eliminate both jumps. This optimization will only remove the second jump, because before that removal the first
one doesn't target the next instruction. However, such sequences aren't typically generated by the compiler.

## Expression Optimization

This optimization looks for sequences of mathematical operations that can be performed more efficiently. Currently,
the following optimizations are available:

* `floor` function called on a multiplication by a constant or a division. Combines the two operations into one
  integer division (`idiv`) operation. In the case of multiplication, the constant operand is inverted to become the
  divisor in the `idiv` operation.
* All set instructions assigning a variable to itself (e.g. `set x x`) are removed. 

## If Expression Optimization

This optimization consists of three types of modifications performed on blocks of code created by if/ternary
expressions. All possible optimizations are done independently.

### Value propagation

The value of ternary expressions and if expressions is sometimes assigned to a user-defined variable. In these
situations, the true and false branches of the if/ternary expression assign the value to a temporary variable, which
is then assigned to the user variable. This optimization detects these situations and when possible, assigns the
final value to the user variable directly in the true/false branches:

```
abs = if x < 0
    negative += 1;  // The semicolon is needed to separate the two statements
    -x
else
    positive += 1
    x
end
```

produces this code:

```
jump 4 greaterThanEq x 0
op add negative negative 1
op mul abs x -1
jump 6 always 0 0
op add positive positive 1
set abs x
end
```

As the example demonstrates, value propagation works on more than just the `set` instruction. All instructions
producing a single value are handled, specifically:

* `set`
* `op`
* `read`
* `sensor`
* `packcolor`

### Forward assignment

Some conditional expressions can be rearranged to save instructions while keeping execution time unchanged:

```
print(x < 0 ? "negative" : "positive")
```

Without If Expression Optimization, the produced code is

```
jump 3 greaterThanEq x 0
set __tmp1 "negative"
jump 4 always 0 0
set __tmp1 "positive"
print __tmp1
```

Execution speed:
* x is negative: 4 instructions (0, 1, 2, 4) are executed,
* x is positive: 3 instructions (0, 3, 4) are executed.

The if expression optimization turns the code into this:

```
set __tmp1 "positive"
jump 3 greaterThanEq x 0
set __tmp1 "negative"
print __tmp1
```

Execution speed:
* x is negative: 4 instructions (0, 1, 2, 3) are executed,
* x is positive: 3 instructions (0, 1, 3) are executed.

The execution time is the same. However, one less instruction is generated.

The forward assignment optimization can be done if at least one of the branches consist of just one instruction, and
both branches produce a value which is then used. Depending on the type of condition and the branch sizes,
either true branch or false branch can get eliminated this way. Average execution time remains the same, although in
some cases the number of executed instructions per branch can change by one (total number of instructions executed
by both branches remains the same).

### Compound condition elimination

The instruction generator always generates true branch first. In some cases, the jump condition cannot be expressed
as a single jump and requires additional instruction (this only happens with the strict equality operator `===`,
which doesn't have an opposite operator in Mindustry Logic).

The additional instruction can be avoided when the true and false branches in the code are swapped. When this
optimizer detects such a situation, it does exactly that:

```
if @unit.dead === 0
    print("alive")
else
    print("dead")
end
```

Notice the `print("dead")` occurs before `print("alive")` now:

```
sensor __tmp0 @unit @dead
jump 4 strictEqual __tmp0 0
print "dead"
jump 0 always 0 0
print "alive"
end
```

### Chained if-else statements

The `elsif` statements are equivalent to nesting the elsif part in the `else` branch of the outer expression.
Optimizations of these nested statements work as expected:

```
y = if x < 0
    "negative"
elsif x > 0
    "positive"
else
    "zero"
end
```

produces

```
set y "negative"
jump 5 lessThan x 0
set y "zero"
jump 5 lessThanEq x 0
set y "positive"
end
```

saving three instructions over the code without if statement optimization:

```
jump 3 greaterThanEq x 0
set __tmp1 "negative"
jump 8 always 0 0
jump 6 lessThanEq x 0
set __tmp3 "positive"
jump 7 always 0 0
set __tmp3 "zero"
set __tmp1 __tmp3
set y __tmp1
end
```

## Data Flow Optimization

This optimization inspects the actual data flow in the program and removes instructions and variables (both user
defined and temporary) that are dispensable or have no effect on the program execution. Each individual optimization
performed is described separately below.

Data Flow Optimizations can have profound effect on the resulting code. User-defined variables can get completely
eliminated, and variables in expressions can get replaced by various other variables that were determined to hold the
same value. The goal of these replacements is to allow elimination of some instructions. The optimizer doesn't try to
avoid variable replacements that do not lead to instruction elimination - this would make the resulting code more
understandable, but the optimizer would have to be more complex and therefore more prone to errors.

> **Note:** One of Mindcode goals is to facilitate making small changes to the compiled code, allowing users to change
> crucial parameters in the compiled code without a need to recompile entire program. To this end, assignments to
> [global variables](SYNTAX-1-VARIABLES.markdown#global-variables) are never removed. Any changes to `set`
> instructions in the compiled code assigning value to global variables are fully supported and the resulting program
> is fully functional, as if the value was assigned to the global variable in the source code itself.
>
> All other variables, including [main variables](SYNTAX-1-VARIABLES.markdown#main-variables) and
> [local variables](SYNTAX-1-VARIABLES.markdown#local-variables), can be completely removed by this optimization.
> Even if they stay in the compiled code, changing the value assigned to a main variable may not produce the same
> effect as compiling the program with the other value. In other words, **changing a value assigned to main variable
> in the compiled code may break the compiled program.**
>
> If you wish to create a parametrized code, follow these rules for best results:
>
> * Use global variables as placeholders for the parametrized values.
> * Assign the parameter values at the very beginning of the program (this way the parameters will be easy to find in
    >   the compiled code).
> * If you sanitize or otherwise modify the parameter value before being used by the program, store the results
    >   of these operations in non-global variables, unless you need them to be global (accessible from multiple
    >   functions).
> * Do not modify instructions other than `set` instructions assigning values to global variables in the compiled code.

### Optimization levels

On `basic` optimization level, the Data Flow Optimization preserves last values assigned to main variables (except
main variables that served as a loop control variable in at least one unrolled loop). This protection is employed to
allow trying out snippets of code in the web application (which runs on `basic` optimization level by default)
without the optimizer eliminating most or all of the compiled code due to it not having any effect on the Mindustry
world.

```
#set optimization = basic
x0 = 0.001
y0 = 0.002
x1 = x0 * x0 + y0 * y0
y1 = 2 * x0 * y0
```

produces

```
set x0 0.001
set y0 0.002
op add x1 0.000001 0.000004
op mul y1 0.002 0.002
end
```

On `aggressive` optimization level, no special protection to main variables is awarded, and they can be completely
removed from the resulting code:

```
#set optimization = aggressive
x0 = 0.001
y0 = 0.002
x1 = x0 * x0 + y0 * y0
y1 = 2 * x0 * y0
```

produces

```
end
```

All the assignment were removed as they wouldn't have any effect on the Mindustry world when run in actual logic
processor.

### Handling of uninitialized variables

The data flow analysis reveals cases where variables might not be properly initialized, i.e. situations where a
value of a variable is read before it is known that some value has been written to the variable. Warnings are
generated for each uninitialized variable found.

Since Mindustry Logic executes the code repeatedly while preserving variable values, not initializing a variable
might be a valid choice, relying on the fact that all variables are assigned a value of `null` by Mindustry at the
beginning. If you intentionally leave a variable uninitialized, you may just ignore the warning. To avoid the
warning, move the entire code into an infinite loop and initialize the variables before that loop. For example:

```
count += 1
print(count)
printflush(message1)
```

can be rewritten as

```
count = 0
while true
    count += 1
    print(count)
    printflush(message1)
end
```

Data Flow Optimization assumes that values assigned to uninitialized variables might be reused on the next program
execution. Assignments to uninitialized variables before calling the `end()` function are therefore protected, while
assignments to initialized variables aren't - they will be overwritten on the next program execution anyway:

```
foo = rand(10)
if initialized == 0
    print("Initializing...")
    // Do some initialization
    initialized = 1
    foo = 1
    end()
end
print("Doing actual work")
print(initialized)
print(foo)
```

produces this code:

```
op rand foo 10 0
jump 5 notEqual initialized 0
print "Initializing..."
set initialized 1
end
print "Doing actual work"
print initialized
print foo
end
```

Notice the `initialized = 1` statement is preserved, while `foo = 1` is not.

This protection is also applied to assignment to uninitialized variables made before calling a user function which,
directly or indirectly, calls the `end()` function:

```
print(foo)
foo = 5
bar()
foo = 6
bar()

def bar()
    end()
end
```

preserves both assignments to `foo`:
```
print foo
set foo 5
jump 6 always 0 0
set foo 6
jump 6 always 0 0
end
end
end
```

See also [`end()` function](SYNTAX-3-STATEMENTS.markdown#end-function).

### Unnecessary assignment elimination

All assignments to variables (except global variables) are inspected and unnecessary assignments are removed. The
assignment is unnecessary if the variable is not read after being assigned, or if it is not read before another
assignment to the variable is made:

```
a = rand(10)
a = rand(20)
print(a)
a = rand(30)
```

compiles to:

```
op rand a 20 0
print a
op rand a 30 0
end
```

The first assignment to `a` is removed, because `a` is not read before another value is assigned to it. The last
assignment to `a` is [preserved on `basic` optimization level](#optimization-levels), but would be removed on
`aggressive` level, because `a` is not read after that assignment at all.

An assignment can also become unnecessary due to other optimizations carried by this optimizer.

### Constant propagation

When a variable is used in an instruction and the value of the variable is known to be a constant value, the variable
itself is replaced by the constant value. This can in turn make the original assignment unnecessary. See for example:

```
a = 10
b = 20
c = @tick + b
printf("$a, $b, $c.")
```

produces

```
op add c @tick 20
print "10, 20, "
print c
print "."
end
```

### Constant folding

Constant propagation described above ensures that constant values are used instead of variables where possible. When
a deterministic operation is performed on constant values (such as addition by the `op add` instruction), constant
folding evaluates the expression and replaces the operation with the resulting value, eliminating an instruction.
For example:

```
a = 10
b = 20
c = a + b
printf("$a, $b, $c.")
```

produces

```
print "10, 20, 30."
end
```

Looks quite spectacular, doesn't it? Here's what happened:

* The optimizer figured out that variables `a` and `b` are not needed, because they only hold a constant value.
* Then it found out the `c = a + b` expression has a constant value too.
* What was left was a sequence of print statements, each printing a constant value.
  [Print Merging optimization](#print-merging) on `aggressive` level then merged it all together.

Not every opportunity for constant folding is detected at this moment. While `x = 1 + y + 2` is optimized to
`op add x y 3`, `x = 1 + y + z + 2` it too complex to process as this moment and the constant values of `1` and `2`
won't be folded at compile time.

If the result of a constant expression doesn't have a valid mlog representation, the optimization is not performed.
In other cases, [loss of precision](SYNTAX.markdown#numeric-literals-in-mindustry-logic) might occur.

### Common subexpressions optimization

The Data Flow Optimizer keeps track of expressions that have been evaluated. When the same expression is encountered
for a second (third, fourth, ...) time, the result of the last computation is reused instead of evaluating the
expression again. In the following code:

```
a = rand(10)
b = a + 1
c = 1 + a + 1
d = 1 + a + 2
print(a, b, c, d)
```

the optimizer notices that the value `a + 1` was assigned to `b` after it was computed for the first time
and reuses it in the subsequent instructions:

```
op rand a 10 0
op add b a 1
op add c a 2
op add d a 3
print a
print b
print c
print d
end
```

Again, not every possible opportunity is used. Instructions are not rearranged, for example, even if doing so would
allow more evaluations to be reused.

On the other hand, entire complex expressions are reused if they're identical. In the following code

```
a = rand(10)
b = rand(10)
x = 1 + sqrt(a * a + b * b)
y = 2 + sqrt(a * a + b * b)
print(x, y)
```

the entire square root is evaluated only once:

```
op rand a 10 0
op rand b 10 0
op mul __tmp2 a a
op mul __tmp3 b b
op add __tmp4 __tmp2 __tmp3
op sqrt __tmp5 __tmp4 0
op add x 1 __tmp5
op add y 2 __tmp5
print x
print y
end
```

### Function call optimizations

Variables and expressions passed as arguments to inline functions, as well as return values of inline functions, are
processed in the same way as other local variables. Using an inlined function therefore doesn't incur any overhead
at all in Mindcode.

Data flow analysis, with some restrictions, is also applied to stackless and recursive function calls. Assignments
to global variables inside stackless and recursive functions are tracked and properly handled. Optimizations are
applied to function arguments and return values. (This optimization has completely replaced earlier _Function call
optimization_ and _Return value optimization_ - all optimizations that could be performed by those optimizations -
and some that couldn't - are performed by Data Flow Optimization now.)

## Loop Optimization

The loop optimization improves loops with the condition at the beginning by performing these modifications:

* If the loop jump condition is invertible, the unconditional jump at the end of the loop to the loop condition is 
  replaced by a conditional jump with inverted loop condition targeting the first instruction of the loop body. This 
  doesn't affect the number of instructions, but executes one less instruction per loop.
  * If the loop condition isn't invertible (that is, the jump condition is `===`), the optimization isn't done, 
    since the saved jump would be spent on inverting the condition, and the code size would increase for no benefit 
    at all.  
* If the previous optimization was done, the optimization level is set to `aggressive`, and the loop condition is 
  known to be true before the first iteration of the loop, the optimizer removes the jump at the front of the loop. 
  Only the simplest cases, where the loop control variable is set by an instruction immediately preceding the front 
  jump and the jump condition compares the control variable to a constant, are handled. Many loop conditions fit these 
  criteria though, namely all constant-range iteration loops.
  * Note: this particular optimization was superseded by the Data Flow Optimization, which - in conjunction with 
    other optimizations - is capable of removing unnecessary assignments and conditional jumps.
* Loop conditions that are complex expressions spanning several instructions can still be replicated at the 
  end of the loop, if the code generation goal is set to `speed` (the default setting at the moment). As a result, 
  the code size might actually increase after performing this kind of optimization. See 
  [optimization for speed](#optimization-for-speed) for details on performing this kind of optimizations.

The result of the first two optimizations in the list can be seen here:

```
#set loop-optimization = aggressive
LIMIT = 10
for i in 0 ... LIMIT
    cell1[i] = 1
end
print("Done.")
```

produces 

```
set LIMIT 10
set i 0
jump 6 greaterThanEq 0 LIMIT
write 1 cell1 i
op add i i 1
jump 3 lessThan i LIMIT
print "Done."
end
```

Executing the entire loop (including the `i` variable initialization) takes 32 steps. Without optimization, the loop 
would require 43 steps. That's quite significant difference, especially for small loops.

The third modification is demonstrated here:

```
#set loop-optimization = aggressive
#set goal = speed

while switch1.enabled and switch2.enabled
    print("Doing something.")
end
print("A switch has been reset.")
```

which produces:

```
sensor __tmp0 switch1 @enabled
sensor __tmp1 switch2 @enabled
op land __tmp2 __tmp0 __tmp1
jump 9 equal __tmp2 false
print "Doing something."
sensor __tmp0 switch1 @enabled
sensor __tmp1 switch2 @enabled
op land __tmp2 __tmp0 __tmp1
jump 4 notEqual __tmp2 false
print "A switch has been reset."
end
```

## Loop Unrolling

Loop unrolling is a [speed optimization](#optimization-for-speed), and as such is only active when the
[goal option](#option-goal) is set to `speed` or `auto`. Furthermore, loop unrolling depends on the [Data 
Flow optimization](#data-flow-optimization) and isn't functional when Data Flow Optimization is not active.

### The principle of loop unrolling

Loop unrolling optimization works by replacing loops whose number of iterations can be determined by the compiler 
with a linear sequence of instructions. This results in speedup of program execution, since the jump instruction 
representing a condition which terminates the loop, and oftentimes also instruction(s) that change the loop control 
variable value, can be removed from the unrolled loop and only instructions actually performing the intended work of 
the loop remain. The optimization is most efficient on loops that are very "tight" - contain very little 
instructions apart from the loop itself. The most dramatic practical example is probably something like this:

```
#set loop-unrolling = off
for i in 0 ... 10
    cell1[i] = 0
end
```

This code clears the first ten slots of a memory cell. Without loop unrolling, the code would look like this:

```
set i 0
write 0 cell1 i
op add i i 1
jump 2 lessThan i 10
```

It takes 31 instruction executions to perform this code. With loop unrolling, though, the code is changed to this:

```
write 0 cell1 0
write 0 cell1 1
write 0 cell1 2
write 0 cell1 3
write 0 cell1 4
write 0 cell1 5
write 0 cell1 6
write 0 cell1 7
write 0 cell1 8
write 0 cell1 9
```

The size of the loop is now 10 instructions instead of 4, but it takes just these 10 instructions to execute, 
instead of the 31 in the previous case, executing three times as fast!

The price for this speedup is the increased number of instructions themselves. Since there's a hard limit of 1000 
instructions in a Mindustry Logic program, loops with large number of iterations obviously cannot be unrolled. See 
[speed optimization](#optimization-for-speed) for an explanation of how Mindcode decides whether to unroll a loop.

Apart from removing the superfluous instructions, loop unrolling also replaces variables with constant values. This 
can make further optimizations opportunities possible, especially for a Data Flow Optimizer and possibly for others. 
A not particularly practical, but nonetheless striking example is this program which computes the sum of numbers from 
0 to 100:

```
sum = 0
for i in 0 .. 100
    sum += i
end
print(sum)
```

which compiles to (drumroll, please):

```
print 5050
end
```

What happened here is this: the loop was unrolled to individual instructions in this basic form:

```
set sum 0
set i 0
op add sum sum i
op add i i 1 
op add sum sum i
op add i i 1 
...
```

Data Flow Optimization then evaluated all those expressions using the combination of [constant
propagation](#constant-propagation) and [constant folding](#constant-folding), all the way into the final sum of
5050, which is then directly printed.

### Loop unrolling preconditions

A list iteration loop can be always unrolled, if there's enough instruction space left. 

For other loops, unrolling can generally be performed when Mindcode can determine the loop has a certain fixed
number of iterations and can infer other properties of the loop, such as a variable that controls the loop 
iterations. A loop should be eligible for the unrolling when the following conditions are met:

* The loop is controlled by a single, non-global variable: the loop condition must consist of a variable which is 
  modified inside a loop, and a constant or an effectively constant variable. Loops based on global variables cannot 
  be unrolled. 
* The loop control variable is modified inside the loop only by `op` instructions which have the loop control variable 
  as a first argument and a constant or an effectively constant variable as a second argument; the op instructions 
  must be deterministic. Any other instruction that sets the value of loop control variable precludes loop unrolling.
* All modifications of the loop control variable happen directly in the loop body: the variable must not be modified 
  in a nested loop or in an if statement, for example.
* The loop control variable isn't a global variable.
* The loop has a nonzero number of iterations. The upper limit of the number of iterations depends on available 
  instruction space, but generally can never exceed 1000 iterations.

Furthermore:

* If the optimization level is `basic`, the loop control variable can only be modified by `add` and `sub` operations.
  Every value the loop control variable attains in individual iterations must be an integer (it means that the 
  starting value, ending value and every change of the iteration variable must be an integer as well). The loop 
  condition must be expressed using one of these operators: `>`, `<`, `>=` or `<=`. In this mode, the total number 
  of iterations is computed using the starting and ending value of the variable and the change in each iteration 
  (resulting in a fast computation).
* If the optimization level is `aggressive`, every deterministic update of loop control variable by a constant value 
  and every form of loop condition is allowed. In this case, Mindcode determines the total number of iterations by 
  emulating the entire execution of the loop, until the loop exits or the maximum possible number of iterations 
  allowed by available instruction space is reached (meaning the loop cannot be unrolled).   
* `break` and `continue` statements are supported. However, when using a `break` statement, the entire loop is still 
  unrolled. Possible superfluous code after a break statement might be removed by other optimizers.

Examples of loops that can be unrolled on `basic` optimization level:

```
// Basic case
for i in 0 .. 10
    cell1[i] = cell1[i + 1]
end

// Two separate increments of the loop control variable
j = 0
while j < 20
    println(j)
    j += 1
    println(j)
    j += 2
end

// The loop control variable can be used in further expressions
for k in 0 ... 10
    cell1[k] = cell1[2 * k]
end

for l in 0 ... 10
    println(l % 2 ? "Odd" : "Even")
end

// Loop inside an inline function: can be unrolled if a constant value is passed into the size parameter
inline def copy(src, dst, size)
    for i in 0 ... size
        dst[i] = src[i]
    end
end

// This will produce a loop that can be unrolled
copy(cell1, cell2, 10)

// This produces a loop that CANNOT be unrolled: SIZE is not a constant value
SIZE = 10
copy(cell1, cell2, SIZE)

// Some loops containing expressions in the condition can still be unrolled,
// but it strongly depends on the structure of the expression
i = 0
while i += 1 < 10
    print(i)
end 
```

Examples of loops that can be unrolled on `aggressive` optimization level:

```
// An operation different from add and sub is supported
for i = 1; i < 100000; i <<= 1
    println(i)
end

// This loop can be unrolled, because it terminates
// (if the condition was j > 0, it would never terminate)
j = 10
while j > 1
    j += 1
    println(i)
    j \= 2
    println(i)
end

// This loop is unrolled, but the number of iterations is 11!
// The code produces the same output as if it wasn't unrolled.
// This is because of rounding errors when evaluating floating-point expressions 
for k = 0; k < 1; k += 0.1
    println(k)
end
```

Examples of loops that **cannot** be unrolled:

```
// LIMIT is a global variable and as such the vslue assigned to it isn't considered constant
// (see Data Flow Optimization)
LIMIT = 10
for i in 0 ... LIMIT
    cell1[i] = 0
end

// The loop control variable is changed inside an if
i = 0
while i < 10
    i += 1
    print(i)
    if i % 5 == 0
        i += 1
        print("Five")
    end
end

// There isn't a single loop control variable - loop condition depens on both i and j
for i = 0, j = 10; i < j; i += 1, j -= 1
    print(i)
end

// The expression changing the loop control variable is too complex.
// (Rewriting the assignment to i *= 2, i += 1 would allow unrolling) 
i = 0
while i < 1000
  i = 2 * i + 1
  print(i)
end

// This loop won't be unrolled. We know it ends after 5 iterations due to the break statement,
// but Mindcode assumes 2000 iterations, always reaching the instruction limit.  
for i in 0 ... 2000
    if i > 5 break end
    print(i)
end
```

### Unrolling nested loops

Nested loops can also be unrolled, and the optimizer prefers unrolling the inner loop:

```
k = 0
for i in 0 ... 100
    for j in 0 ... 100
        k = k + rand(j) // Prevents collapsing the loop by Data Flow Optimization 
    end
end
```

Both loops are eligible for unrolling at the beginning, and the inner one is chosen. After that the outer loop can 
no longer be unrolled, because the instruction limit would be hit.

Sometimes unrolling an outer loop can make the inner loop eligible for unrolling too. In this case, the inner loop 
cannot be unrolled first, as it is not constant:

```
#set optimization = aggressive
first = true
for i in 1 .. 5
    for j in i .. 5
        if first
            first = false
        else
            print(" ")
        end
        print(10 * i + j)
    end
end
```

In this case, the outer loop is unrolled first, after which each copy of the inner loop can be unrolled 
independently. Further optimizations (including Print Merging) then compact the entire computation into a single 
print instruction:

```
print "11 12 13 14 15 22 23 24 25 33 34 35 44 45 55"
end
```

## Function Inlining

Function Inlining is a [speed optimization](#optimization-for-speed), and as such is only active when the
[goal option](#option-goal) is set to `speed` or `auto`.

### The principles of function inlining

Function inlining converts out-of-line function calls into inline function calls. This conversion alone saves a few
instructions: storing the return address, jumping to the function body and jumping back at the original address. 
However, many additional optimizations might be available once a function is inlined, especially if the inlined 
function call is using constant argument values. In such a situation many other powerful optimizations, such as 
constant folding or loop unrolling, may become available.

User defined, non-recursive function which is called just once in the entire program, is automatically inlined and 
this cannot be prevented: such code is always both faster and smaller. It is also possible to declare individual 
functions using the `inline` keyword, forcing all calls of such functions to be inlined.

### Automatic function inlining

This optimization can inline additional functions that aren't recursive and also aren't declared `inline` in the 
source code. If there's enough instruction space, all function calls may be inlined and the original function body 
removed from the program. 

When the optimization level is set to `aggressive` and there isn't enough instruction space, only a single one or 
several specific function calls may be inlined; in such case the original function body remains in the program and 
is used by the function calls that weren't inlined. If there are only last two function calls remaining, either both 
of them will be inlined, or none of them.    

It is therefore no longer necessary to use the `inline` keyword, except in cases when Mindcode's automatic inlining 
chooses function different from the one(s) you prefer to be inlined. 

## Jump Straightening

This optimization detects situations where a conditional jump skips a following, unconditional one and replaces it
with a single conditional jump with a reversed condition and a target of the second jump. Example:

```
jump __label0 equal __tmp9 false
jump __label1
label __label0
```

will be turned to

```
jump __label1 notEqual __tmp9 false
```

Optimization won't be done if the condition does not have an inverse (`strictEqual`).

These sequences of instructions may arise when using the `break` or `continue` statements:

```
while true
    ...
    if not_alive
        break
    end
end
```

## Jump Threading

If a jump (conditional or unconditional) targets an unconditional jump, the target of the first jump is redirected
to the target of the second jump, repeated until the end of jump chain is reached. Moreover:

* on `aggressive` level, `end` instruction is handled identically to `jump 0 always`,
* conditional jumps in the jump chain are followed if
  * their condition is identical to the condition the first jump, and
  * the condition arguments do not contain a volatile variable (`@time`, `@tick`, `@counter` etc.).

No instructions are removed or added, but the execution of the code is faster.

## Unreachable Code Elimination

This optimizer removes instructions that are unreachable. There are several ways unreachable instructions might appear:

1. Jump Threading can create unreachable jumps that are no longer targeted.
2. User-created unreachable regions, such as `while false ... end`, or code following a `while true` loop.
3. User defined functions which are called from an unreachable region.

Instruction removal is done by analyzing the control flow of the program and removing instructions that are never 
executed. When [Jump Normalization](#jump-normalization) is not active, some section of unreachable code may not be 
recognized.

The `end` instruction, even when not reachable, is not removed unless the optimization level is `aggressive`.
Main body program and function definitions are each terminated by the `end` instruction and removing it
might make the produced code somewhat less readable.

## Stack Optimization

Optimizes the stack usage -- eliminates `push`/`pop` instruction pairs determined to be unnecessary. The following 
optimizations are performed:

* `push`/`pop` instruction elimination for function variables that are not used anywhere apart from the push/pop 
  instructions. This happens when variables are eliminated by other optimizations. The optimization is done globally,
  in a single pass across the entire program.
* `push`/`pop` instruction elimination for function variables that are read neither by any instruction between the 
  call instruction and the end of the function, nor by any instruction which is part of the same loop as the call 
  instruction. Implicit reads by recursive calls to the same function with the value of the parameter unchanged are 
  also detected.
* `push`/`pop` instruction elimination for function parameters/variables that are never modified within the function.

## Print Merging

This optimization merges together print instructions with string literal arguments.
The print instructions will get merged even if they aren't consecutive, assuming there aren't instructions
that could break the print sequence (`jump`, `label` or `print <variable>`).

Effectively, this optimization eliminates a `print` instruction by turning this

```
println("Items: ", items)
println("Time: " @time)
```
into this:

```
print("Items: ", items)
print("\nTime: ", @time "\n")
```

On `aggressive` level, all constant values - not just string constants - are merged. For example:

```
#set optimization = aggressive
const MAX_VALUE = 10
printf("Step $i of $MAX_VALUE\n")
```

produces
```
print "Step "
print i
print " of 10\n"
```

On basic optimization level, the output would be:

```
print "Step "
print i
print " of "
print 10
print "\n"
```
### String length limit

On `basic` level, the optimization won't merge print instructions if the merge would produce a string
longer than 34 characters (36 when counting the double quotes). On `aggressive` level, such instructions
will be merged regardless. This can create long string constants, but according to our tests these can be pasted
into Mindustry processors even if they're longer than what the Mindustry GUI allows to enter.

# Optimization for speed

In some cases, it is possible to rearrange the code in such a way that, even though it consist of more instructions, 
it actually executes faster. An example of this kind of optimization is function call inlining: instead of having a 
single copy of the function called from various places, the entire code of the function can be replicated at the 
places it is called from. The speedup stems from the fact that passing arguments to the function, storing the return 
address and later returning back from the function can be avoided this way, while the code size increase is a 
consequence of having two or more copies of the function instead of just one.

Mindustry processors limit the total number of instructions in a program to a thousand. If the compiled code exceeds 
this limit, it is unusable, but if the compiled code is smaller than the limit, the savings bring no benefit at all. 
The best approach therefore is to use as much of the existing instruction space without exceeding the limit to 
generate faster code where possible.

Mindcode provides the [`goal` option](#option-goal) to specify whether Mindcode should generate code that is smaller
(the goal is `size`) or a code that is faster (`speed`). There's a third option - `auto` - which is the default for 
the compiler and currently is identical to the `speed` option. When the goal is set to `size`, no optimization for 
speed, described in this chapter, happens.

When generating the code, there might be several opportunities for speed optimizations. It is possible that by 
applying all of them the limit of code size would be exceeded. Mindcode follows the following process to choose the 
optimizations that will be realized:

* At certain point in the optimization process, all opportunities for speed optimizations are analyzed and those that 
  can be realized in the remaining instruction space are gathered.
* For each such possible optimization, the following quantities are computed:
  * **cost**: the number of additional instructions that will be created by the optimization,
  * **benefit**: a measure of execution improvement achieved by the optimization,
  * **efficiency**: the efficiency of the optimization is computed by dividing the benefit by cost, obtaining a 
    measure of program improvement per additional instruction of code.
* The optimization with the highest efficiency is applied.
* The entire process is repeated from start until there are no optimization possibilities left, or none of the 
  optimizations is permissible given available instruction space.

In short, as many as possible optimizations are applied in the order from one giving best returns to the 
one giving worst return. Different strategies for choosing optimizations might be possible (for example, instead of 
realizing one large optimization, it might be better to apply two smaller ones), but at this point these 
different arrangements aren't considered. 

Oftentimes, an optimization being applied might open up opportunities for further optimizations (in some cases it 
might be possible to unroll inner loop after unrolling the outer loops, for example). Since all possible 
optimizations are considered after applying the most efficient one, Mindcode will automatically discover and process 
these additional optimizations (in the described order).

It's quite obvious that calculating the benefit is a key part of the optimization evaluation. Avoiding an 
instruction that is executed often provides more benefit that avoiding one that is executed just once (in Mindustry, 
each instruction takes the same time to execute, simplifying the things greatly). Mindcode therefore assigns each 
instruction a _weight_, a measure of how often the instruction is expected to be executed. In general, it is 
impossible to compute this number precisely. Mindcode uses a very simple algorithm to determine instruction weights:

* At the beginning of code generation, current weight is established:
  * 1 for the main program,
  * number of places a function is called from for out-of-line or recursive functions. 
* Each generated instruction is assigned the current weight.
* When entering a branch of an `if` statement, the current weight is halved. It is restored when exiting the branch. 
  This corresponds to a (somewhat naive) expectation that the condition will be evaluated to true 50% of the time, 
  and therefore a branch in the if statement will be executed only half as often as the surrounding code.  
* When entering a `when` branch of a `case` expression, the weight is divided by the number of branches in the 
  expression. The reasoning (and inaccuracy) is analogous to the `if` statement approximation described above.
* When entering a loop body, the weight is multiplied by 25. The implicit expectation is that a loop will iterate 25 
  times on average (which is certainly wrong). Even when the number of iterations is known at the compilation time, 
  the weight adjustment is the same for all loops. This is to prevent Mindcode from preferring optimizations of 
  loops with large, known number of iterations (such as setting all values of memory bank to 0) to other loops whose 
  number of iterations cannot be known.   

The benefit of an optimization is then computed as the sum of weights of instructions that would be avoided thanks 
to the optimization. The net result is that Mindcode strongly prefers optimizing code inside loops, and defers 
optimizations inside branches of `if` and `case` statements.   

---

[« Previous: Functions](SYNTAX-4-FUNCTIONS.markdown) &nbsp; | &nbsp; [Next: Schemacode »](SCHEMACODE.markdown)
