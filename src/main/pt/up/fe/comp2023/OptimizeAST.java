package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;

import java.util.*;

public class OptimizeAST {

    private SymbolTable symbolTable = new SymbolTable();
    private JmmNode rootNode;

    private HashSet<String> deadVariables = new HashSet<>();

    private HashMap<String, JmmNode> variables = new HashMap<>();

    private void removeDeadVars(JmmNode start) {
        for (JmmNode node : start.getChildren()) {
            removeDeadVars(node);
            if (node.getKind().equals("Assignment") && node.hasAttribute("variable") && deadVariables.contains(node.get("variable"))) {
                node.delete();
            }
        }
    }

    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        symbolTable = (SymbolTable) semanticsResult.getSymbolTable();
        rootNode = semanticsResult.getRootNode();

        boolean alterations = true;

        while (alterations) {
            boolean b1 = constantPropagationOptimization(rootNode);
            boolean b2 = constantFoldingOptimization(rootNode);
            alterations = b1 || b2;
        }

        removeDeadVars(rootNode);
        deadVariables.clear();

        return semanticsResult;
    }


    public void removeAllChildren(JmmNode jmmNode) {
        while (jmmNode.getNumChildren() != 0) {
            jmmNode.removeJmmChild(0);
        }
    }

    private String computeIntegerOperation(int child1Value, int child2Value, String op) {
        return switch (op) {
            case "+" -> String.valueOf(child1Value + child2Value);
            case "-" -> String.valueOf(child1Value - child2Value);
            case "*" -> String.valueOf(child1Value * child2Value);
            case "/" -> String.valueOf(child1Value / child2Value);
            case "<" -> String.valueOf(child1Value < child2Value);
            case ">" -> String.valueOf(child1Value > child2Value);
            case "<=" -> String.valueOf(child1Value <= child2Value);
            case ">=" -> String.valueOf(child1Value >= child2Value);
            case "==" -> String.valueOf(child1Value == child2Value);
            case "!=" -> String.valueOf(child1Value != child2Value);
            default -> "";
        };
    }

    private String computeBooleanOperation(boolean child1Value, boolean child2Value, String op) {
        return switch (op) {
            case "==" -> String.valueOf(child1Value == child2Value);
            case "!=" -> String.valueOf(child1Value != child2Value);
            case "&&" -> String.valueOf(child1Value && child2Value);
            case "||" -> String.valueOf(child1Value || child2Value);
            default -> "";
        };
    }


    public boolean binaryOpOptimization(JmmNode jmmNode) {
        JmmNode child1 = jmmNode.getJmmChild(0), child2 = jmmNode.getJmmChild(1);
        if (child1.getKind().equals("Literal") && child2.getKind().equals("Literal")) {
            if (child1.hasAttribute("integer") && child2.hasAttribute("integer")) {
                int child1Value = Integer.parseInt(child1.get("integer")), child2Value = Integer.parseInt(child2.get("integer"));
                String result = computeIntegerOperation(child1Value, child2Value, jmmNode.get("op"));
                String varType = (result.equals("true") || result.equals("false")) ? "bool" : "integer";

                removeAllChildren(jmmNode);

                JmmNode newNode = new JmmNodeImpl("Literal");
                newNode.put("varType", varType);
                newNode.put("isArray", "false");
                newNode.put(varType, result);

                jmmNode.replace(newNode);

            } else if (child1.hasAttribute("bool") && child2.hasAttribute("bool")) {
                boolean child1Value = child1.get("bool").equals("true"), child2Value = child2.get("bool").equals("true");
                String result = computeBooleanOperation(child1Value, child2Value, jmmNode.get("op"));

                removeAllChildren(jmmNode);

                JmmNode newNode = new JmmNodeImpl("Literal");
                newNode.put("varType", "boolean");
                newNode.put("isArray", "false");
                newNode.put("bool", String.valueOf(result));

                jmmNode.replace(newNode);
            } else {
                System.out.println("Binary operation not valid on 2 different types.");
            }

            return true;
        } else if (child1.getKind().equals("BinaryOp")) {
            return binaryOpOptimization(child1);
        } else if (child2.getKind().equals("BinaryOp")) {
            return binaryOpOptimization(child2);
        }
        return false;
    }


    public boolean constantFoldingOptimization(JmmNode jmmNode) {
        boolean alterations = false;

        if (jmmNode.getKind().equals("BinaryOp")) {
            alterations = binaryOpOptimization(jmmNode);
        } else {
            for (JmmNode child : jmmNode.getChildren()) {
                boolean result = constantFoldingOptimization(child);
                alterations = alterations || result;
            }
        }

        return alterations;
    }


    private Type matchVariable(List<Symbol> list, String id) {
        for (Symbol s : list) {
            if (s.getName().equals(id)) {
                return new Type(s.getType().getName().equals("int") ? "integer" : s.getType().getName(), s.getType().isArray());
            }
        }
        return null;
    }


    // Retorna se num dado JmmNode a variável var é constante
    public boolean isVarConstant(JmmNode assignment) {

        if (assignment.getAncestor("IfStatement").isPresent() || assignment.getAncestor("WhileLoop").isPresent()) {
            return false;
        } else if (assignment.getJmmChild(0).getKind().equals("LiteralS")) {
            String id = assignment.getJmmChild(0).get("id");

            // check if RHS is a field
            boolean isField = matchVariable(symbolTable.getFields(), id) != null;

            // check if RHS is a parameter
            boolean nodeInsideFunction = assignment.getAncestor("method").isPresent();

            if (!nodeInsideFunction) return false;

            List<Symbol> functionParams = symbolTable.getParameters(assignment.getAncestor("method").get().get("method"));
            boolean isParam = matchVariable(functionParams, id) != null;

            if (isField || isParam) return false;

            return isVarConstant(assignment);

        } else
            return assignment.getJmmChild(0).getKind().equals("Literal");

    }

    public void eraseAssignmentsInsideLoop(JmmNode whileBody) {
        for (JmmNode statement : whileBody.getChildren()) {
            if (statement.getKind().equals("Assignment")) {
                variables.put(statement.get("variable"), null);
            }
        }
    }


    public boolean checkAllInstances(JmmNode currentNode) {

        boolean isConst, alterations = false;

        if (currentNode.getKind().equals("LiteralS")) {
            if (currentNode.getAncestor("WhileLoop").isPresent()) {
                JmmNode whileBody = currentNode.getAncestor("WhileLoop").get().getJmmChild(1);
                eraseAssignmentsInsideLoop(whileBody);
            }

            JmmNode node = variables.get(currentNode.get("id"));

            if (node != null) {
                this.deadVariables.add(currentNode.get("id"));
                currentNode.replace(node);
                alterations = true;
            }

        } else if (currentNode.getKind().equals("Assignment") && currentNode.getNumChildren() == 1) {
            isConst = isVarConstant(currentNode);

            if (isConst) {
                JmmNode newNode = new JmmNodeImpl("Literal");

                String valueAttribute = currentNode.get("varType").equals("boolean") ? "bool" : "integer";
                newNode.put("varType", currentNode.get("varType"));
                newNode.put("isArray", currentNode.get("isArray"));
                newNode.put(valueAttribute, currentNode.getJmmChild(0).get(valueAttribute));

                variables.put(currentNode.get("variable"), newNode);
            } else variables.put(currentNode.get("variable"), null);


        } else if (currentNode.getKind().equals("MethodDeclaration")) {
            variables.clear();
        }

        return alterations;
    }


    public boolean constantPropagationOptimization(JmmNode currentNode) {
        boolean alterations = false;
        for (JmmNode child : currentNode.getChildren()) {
            boolean res = checkAllInstances(child);
            alterations = alterations || res;
            alterations = alterations || constantPropagationOptimization(child);
        }

        return alterations;
    }
}
