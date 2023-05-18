package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class OptimizeAST {

    private SymbolTable symbolTable = new SymbolTable();
    private JmmNode rootNode;


    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult){
        symbolTable = (SymbolTable) semanticsResult.getSymbolTable();
        rootNode = semanticsResult.getRootNode();

        boolean alterations = true;

        while(alterations) {
            boolean b1 = constantPropagationOptimization();
            boolean b2 = constantFoldingOptimization(rootNode);
            alterations = b1 || b2;
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


    public boolean binaryOpOptimization(JmmNode jmmNode){
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

            return true;
        } else if (child1.getKind().equals("BinaryOp")) {
            binaryOpOptimization(child1);
        } else if (child2.getKind().equals("BinaryOp")) {
            binaryOpOptimization(child2);
        } else if (child1.getKind().equals("LiteralS") || child2.getKind().equals("LiteralS")){

        }
        else {
            System.out.println("Invalid Binary Operation.");
        }
        return false;
    }


    public boolean constantFoldingOptimization(JmmNode jmmNode){
        boolean alterations = false;

        if(jmmNode.getKind().equals("BinaryOp")){
            alterations = binaryOpOptimization(jmmNode);
        }
        else{
            for (JmmNode child: jmmNode.getChildren()) {
                boolean result = constantFoldingOptimization(child);
                alterations = alterations || result;
            }
        }

        return alterations;
    }

    public boolean checkIfFieldOrParameter(JmmNode jmmNode){
        Optional<JmmNode> ancestor = jmmNode.getAncestor("MethodDeclaration");
        if(ancestor.isEmpty()){
            return true;
        }

        for (Symbol param: symbolTable.getParameters(ancestor.get().get("methodName"))) {
            if(param.getName().equals(jmmNode.get("variable"))){
                return true;
            }
        }

        if(!ancestor.get().hasAttribute("isStatic")){
            for (Symbol field: symbolTable.getFields()) {
                if(field.getName().equals(jmmNode.get("variable"))){
                    return true;
                }
            }
        }

        return false;
    }

    public Optional<JmmNode> variableIsInHashMap(HashMap<JmmNode, Integer> map, JmmNode jmmNode){
        for (Map.Entry<JmmNode, Integer> entry: map.entrySet()) {
            if(jmmNode.get("variable").equals(entry.getKey().get("variable")))
                return Optional.of(entry.getKey());
        }
        return Optional.empty();
    }


    public HashMap<JmmNode, Integer> getAllVariableAssignments(JmmNode jmmNode, HashMap<JmmNode, Integer> variableAssignments){
        if(jmmNode.getKind().equals("Assignment")){
            if(!checkIfFieldOrParameter(jmmNode)){
                Optional<JmmNode> hashmapKey = variableIsInHashMap(variableAssignments, jmmNode);
                if(hashmapKey.isPresent()){
                    int assignmentCounter = variableAssignments.get(hashmapKey.get());
                    variableAssignments.put(hashmapKey.get(), assignmentCounter + 1);
                } else if(jmmNode.getJmmChild(0).getKind().equals("Literal")){
                    variableAssignments.put(jmmNode, 1);
                }
            }
        }

        for (JmmNode child: jmmNode.getChildren()) {
            getAllVariableAssignments(child, variableAssignments);
        }

        return variableAssignments;
    }

    public boolean replaceAllInstances(JmmNode jmmNode, JmmNode originalNode){
        boolean alterations = false;

        String var = originalNode.get("variable");
        if(originalNode.equals(jmmNode)){
            if(jmmNode.getJmmParent().getKind().equals("VarDeclaration")){
                jmmNode.getJmmParent().delete();
            }
            jmmNode.delete();
        }

        else {
            if(jmmNode.getKind().equals("LiteralS") && jmmNode.get("id").equals(var)){
                JmmNode newNode = new JmmNodeImpl("Literal");

                String valueAttribute = originalNode.get("varType").equals("boolean")? "bool": "integer";
                newNode.put("varType", originalNode.get("varType"));
                newNode.put("isArray", originalNode.get("isArray"));
                newNode.put(valueAttribute, originalNode.getJmmChild(0).get(valueAttribute));

                jmmNode.replace(newNode);

                alterations = true;
            } else if((jmmNode.getKind().equals("VarDeclaration") && jmmNode.hasAttribute("variableName") && jmmNode.get("variableName").equals(var))){
                jmmNode.delete();
                alterations = true;
            }
        }


        for (JmmNode child: jmmNode.getChildren()) {
            boolean result = replaceAllInstances(child, originalNode);
            alterations = alterations || result;
        }

        return alterations;
    }


    // instancias de variaveis = LiteralS
    // 1º substituir todas as instâncias de LiteralS com id = var por value
    // 2º eliminar nó da declaração VarDeclaration (e o primeiro assignment, pode ser filho ou nao)
    // 3º retirar da symbolTable
    public boolean constantPropagationOptimization(){

        HashMap<JmmNode, Integer> variableAssignments = new HashMap<>();
        boolean alterations = false;

        variableAssignments = getAllVariableAssignments(rootNode, variableAssignments);
        for (Map.Entry<JmmNode, Integer> entry: variableAssignments.entrySet()) {
            if(entry.getValue() == 1){
                String funcName = entry.getKey().getAncestor("MethodDeclaration").get().get("methodName");
                boolean result = replaceAllInstances(rootNode, entry.getKey());
                alterations = alterations || result;
                Symbol symbol = new Symbol(new Type(entry.getKey().get("varType").equals("integer")? "int": entry.getKey().get("varType"), entry.getKey().get("isArray").equals("true")), entry.getKey().get("variable"));
                symbolTable.removeLocalVariable(funcName, symbol);
            }
        }

        System.out.println(variableAssignments);
        return alterations;
    }
}
