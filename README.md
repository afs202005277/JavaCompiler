# Compilers Project

## Implemented optimizations

- Constant folding: integer and boolean expressions
- Constant propagation: integer and boolean expressions
- Register allocation
- Dead code elimination (after running constant propagation and folding)
- Usage of low-cost instructions in Jasmin:
  - iload_x, istore_x, astore_x, aload_x
  - iconst_0, bipush, sipush, ldc
  - use of iinc
  - iflt, ifne, etc (compare against zero, instead of two values, e.g., if_icmplt)

## Extras

- Operators: ==, !=, >, >=, <=, ||
- Dead code elimination
- Constant propagation and folding with booleans

## Self Evaluation

We believe our project meets all the mandatory requirements and, as far as we know, there are no faults in its
implementation.
Furthermore, our project passes all public and private tests (of all checkpoints) with success.
Therefore, we believe our project deserves a score of 20.

## Students

- André Sousa (up202005277): 33.3%
- Matilde Silva (up202007928): 33.3%
- Pedro Fonseca (up202008307): 33.3%
