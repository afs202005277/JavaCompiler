# Compilers Project: Java-- Compiler Implementation

This repository contains the implementation of a compiler for **Java--**, a subset of the Java programming language. The goal of this project was to design and implement various optimization techniques and improvements for the Java-- language, focusing on generating efficient Java bytecode through several compiler transformations.

## Features

The Java-- compiler includes several optimizations and additional features to enhance both performance and code efficiency:

### Optimizations

- **Constant Folding**: Optimizes integer and boolean expressions at compile time.
- **Constant Propagation**: Propagates constants in integer and boolean expressions across the program.
- **Register Allocation**: Optimizes the use of registers during compilation to minimize memory usage.
- **Dead Code Elimination**: Removes unnecessary code after constant propagation and folding.
- **Low-Cost Instructions Usage**: Utilizes Jasmin instructions that minimize the bytecode size and improve performance:
  - `iload_x`, `istore_x`, `astore_x`, `aload_x`
  - `iconst_0`, `bipush`, `sipush`, `ldc`
  - Use of `iinc`
  - Conditional jumps such as `iflt`, `ifne` (comparing against zero instead of two values, e.g., `if_icmplt`)

### Additional Features

- Support for operators: `==`, `!=`, `>`, `>=`, `<=`, `||`
- Dead code elimination and constant propagation and folding with boolean expressions.

## Team Members

- **André Sousa** (up202005277)
- **Matilde Silva** (up202007928)
- **Pedro Fonseca** (up202008307)
