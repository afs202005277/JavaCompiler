package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;

import java.util.List;
import java.util.Map;

public class OptimizeAST {

    private SymbolTable symbolTable = new pt.up.fe.comp2023.SymbolTable();
    private JmmNode rootNode;


    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult){
        symbolTable = semanticsResult.getSymbolTable();
        rootNode = semanticsResult.getRootNode();

        boolean alterations = true;

        while(alterations) {
            alterations = constantFoldingOptimization(rootNode);
        }

        return semanticsResult;
    }


    public void removeAllChildren(JmmNode jmmNode){
        while(jmmNode.getNumChildren() != 0){
            jmmNode.removeJmmChild(0);
        }
    }

    private String computeIntegerOperation(int child1Value, int child2Value, String op){
        return switch (op) {
            case "+" -> String.valueOf(child2Value + child1Value);
            case "-" -> String.valueOf(child2Value - child1Value);
            case "*" -> String.valueOf(child2Value * child1Value);
            case "/" -> String.valueOf(child2Value / child1Value);
            case "<" -> String.valueOf(child2Value < child1Value);
            case ">" -> String.valueOf(child2Value > child1Value);
            case "<=" -> String.valueOf(child2Value <= child1Value);
            case ">=" -> String.valueOf(child2Value >= child1Value);
            case "==" -> String.valueOf(child2Value == child1Value);
            case "!=" -> String.valueOf(child2Value != child1Value);
            default -> "";
        };
    }

    private String computeBooleanOperation(boolean child1Value, boolean child2Value, String op){
        return switch (op) {
            case "==" -> String.valueOf(child2Value == child1Value);
            case "!=" -> String.valueOf(child2Value != child1Value);
            case "&&" -> String.valueOf(child2Value && child1Value);
            case "||" -> String.valueOf(child2Value || child1Value);
            default -> "";
        };
    }


    public void binaryOpOptimization(JmmNode jmmNode){
        JmmNode child1 = jmmNode.getJmmChild(0), child2 = jmmNode.getJmmChild(1);

        if(child1.getKind().equals("Literal") && child2.getKind().equals("Literal")){
            if(child1.hasAttribute("integer") && child2.hasAttribute("integer")){
                int child1Value = Integer.parseInt(child1.get("integer")) , child2Value = Integer.parseInt(child2.get("integer"));
                String result = computeIntegerOperation(child1Value, child2Value, jmmNode.get("op"));

                removeAllChildren(jmmNode);

                JmmNode newNode = new JmmNodeImpl("Literal");
                newNode.put("varType", "integer");
                newNode.put("isArray", "false");
                newNode.put("integer", result);

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
            }
            else {
                System.out.println("Binary operation not valid on 2 different types.");
            }

        } else if (child1.getKind().equals("BinaryOp")) {
            binaryOpOptimization(child1);
        } else if (child2.getKind().equals("BinaryOp")) {
            binaryOpOptimization(child2);
        }
        else {
            System.out.println("Invalid Binary Operation.");
        }
    }


    public boolean constantFoldingOptimization(JmmNode jmmNode){
        boolean alterations = false;

        if(jmmNode.getKind().equals("BinaryOp")){
            binaryOpOptimization(jmmNode);
            alterations = true;
        }
        else{
            for (JmmNode child: jmmNode.getChildren()) {
                alterations = alterations || constantFoldingOptimization(child);
            }
        }

        return alterations;
    }

}
