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

    private String computeBooleanOperation(boolean child1Value, boolean child2Value, String op){
        return switch (op) {
            case "==" -> String.valueOf(child1Value == child2Value);
            case "!=" -> String.valueOf(child1Value != child2Value);
            case "&&" -> String.valueOf(child1Value && child2Value);
            case "||" -> String.valueOf(child1Value || child2Value);
            default -> "";
        };
    }


    public boolean binaryOpOptimization(JmmNode jmmNode){
        JmmNode child1 = jmmNode.getJmmChild(0), child2 = jmmNode.getJmmChild(1);
        if(child1.getKind().equals("Literal") && child2.getKind().equals("Literal")){
            if(child1.hasAttribute("integer") && child2.hasAttribute("integer")){
                int child1Value = Integer.parseInt(child1.get("integer")) , child2Value = Integer.parseInt(child2.get("integer"));
                String result = computeIntegerOperation(child1Value, child2Value, jmmNode.get("op"));
                String varType = (result.equals("true") || result.equals("false")) ? "bool": "integer";

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
            }
            else {
                System.out.println("Binary operation not valid on 2 different types.");
            }

            return true;
        } else if (child1.getKind().equals("BinaryOp")) {
            return binaryOpOptimization(child1);
        } else if (child2.getKind().equals("BinaryOp")) {
            return binaryOpOptimization(child2);
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
                if (jmmNode.getJmmChild(0).getKind().equals("LiteralS") && jmmNode.getJmmChild(0).get("id").equals(jmmNode.get("variable"))/*TODO se for um LieralS no right hand side, é preciso lidar*/) {
                    jmmNode.delete();
                }
                else if(hashmapKey.isPresent()){
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



    public static JmmNode findNodeWithTargetValue(JmmNode startNode, String targetVariable) {
        if (startNode == null)
            return null;

        JmmNode currentNode = startNode;
        JmmNode targetNode = null;

        while (currentNode != null) {
            targetNode = findNodeWithTargetValueHelper(currentNode, targetVariable);
            if (targetNode != null)
                break;

            currentNode = currentNode.getJmmParent();
        }

        return targetNode;
    }

    private static JmmNode findNodeWithTargetValueHelper(JmmNode node, String targetVariable) {
        if (node == null)
            return null;

        if (node.getKind().equals("Assignment") && node.get("variable").equals(targetVariable))
            return node;

        ArrayList<JmmNode> reverseChildren = new ArrayList<>(node.getChildren());
        Collections.reverse(reverseChildren);

        for (JmmNode child : reverseChildren) {
            JmmNode result = findNodeWithTargetValueHelper(child, targetVariable);
            if (result != null)
                return result;
        }

        return null;
    }


    public JmmNode getVariableLastAssignment(JmmNode node, String var){
        return findNodeWithTargetValue(node, var);
    }

    private Type matchVariable(List<Symbol> list, String id){
        for (Symbol s: list) {
            if(s.getName().equals(id)){
                return new Type(s.getType().getName().equals("int")? "integer": s.getType().getName(), s.getType().isArray());
            }
        }
        return null;
    }

    // Retorna se num dado JmmNode a variável var é constante
    public boolean isVarConstant(JmmNode node, String var){
        JmmNode assignment = getVariableLastAssignment(node, var);
        if(assignment.getAncestor("IfStatement").isPresent() || assignment.getAncestor("WhileLoop").isPresent()){
            return false;
        } else if (assignment.getJmmChild(0).getKind().equals("LiteralS")) {
            String id = assignment.getJmmChild(0).get("id");

            // check if RHS is a field
            boolean isField = matchVariable(symbolTable.getFields(), id) != null;

            // check if RHS is a parameter
            boolean nodeInsideFunction = node.getAncestor("method").isPresent();

            if(!nodeInsideFunction) return false;

            List<Symbol> functionParams = symbolTable.getParameters(node.getAncestor("method").get().get("method"));
            boolean isParam = matchVariable(functionParams, id) != null;

            if(isField || isParam) return false;

            return isVarConstant(node, id);
        } else if (assignment.getJmmChild(0).getKind().equals("BinaryOp")) {
            JmmNode binOp1 = assignment.getJmmChild(0).getJmmChild(0), binOp2 = assignment.getJmmChild(0).getJmmChild(1);
            boolean res1 = false, res2 = false;

            if(binOp1.getKind().equals("LiteralS")){
                res1 = isVarConstant(node, binOp1.get("id"));
            }
            else if(binOp1.getKind().equals("Literal")){
                res1 = true;
            }

            if (binOp2.getKind().equals("LiteralS")) {
                res2 = isVarConstant(node, binOp2.get("id"));
            }
            else if(binOp2.getKind().equals("Literal")){
                res2 = true;
            }

            return res1 && res2;
        }

        return true;
    }

    // Starting at the assignment node, remove all instances of variable
    public boolean deleteAllInstances(JmmNode currentNode, String id, JmmNode assignmentNode, boolean fairplay){

        if(currentNode.equals(assignmentNode)){
            return true;
        }

        if(currentNode.getKind().equals("LiteralS") && currentNode.get("id").equals(id)){
            JmmNode newNode = new JmmNodeImpl("Literal");

            String valueAttribute = assignmentNode.get("varType").equals("boolean")? "bool": "integer";
            newNode.put("varType", assignmentNode.get("varType"));
            newNode.put("isArray", assignmentNode.get("isArray"));
            newNode.put(valueAttribute, assignmentNode.getJmmChild(0).get(valueAttribute));

            currentNode.replace(newNode);
        } else if (currentNode.getKind().equals("Assignment") && currentNode.get("variable").equals(id)) {
            return false;
        }
        else if(currentNode.getKind().equals("ReturnStmt")){
            return deleteAllInstances(currentNode.getJmmChild(0), id, assignmentNode, fairplay);
        }


        for (JmmNode child: currentNode.getChildren()){
            boolean res = deleteAllInstances(child, id, assignmentNode, fairplay);
            fairplay = fairplay || res;
        }

        return fairplay;
    }

    public void deleteVarDeclaration(JmmNode jmmNode, String var){

        if((jmmNode.getKind().equals("VarDeclaration") && jmmNode.hasAttribute("variableName") && jmmNode.get("variableName").equals(var))){
            jmmNode.delete();
            return;
        }

        for(JmmNode child: jmmNode.getChildren()){
            deleteVarDeclaration(child, var);
        }
    }

    public boolean checkAllInstances(JmmNode currentNode){
        boolean functionAncestor = currentNode.getAncestor("MethodDeclaration").isPresent();
        boolean isConst = false;

        if(currentNode.getKind().equals("LiteralS")){
            isConst = isVarConstant(currentNode, currentNode.get("id"));
            JmmNode assignment = getVariableLastAssignment(currentNode, currentNode.get("id"));
            // se for const, remover o ultimo assignment dela e substituir todos os literalS a partir desse assign
            if(isConst && functionAncestor){
                JmmNode methodNode = currentNode.getAncestor("MethodDeclaration").get();
                assignment.delete();

                JmmNode lastAssignment = getVariableLastAssignment(currentNode, currentNode.get("id"));
                deleteAllInstances(methodNode, currentNode.get("id"), assignment, false);

                if(lastAssignment == null){
                    deleteVarDeclaration(methodNode, currentNode.get("id"));
                    Symbol symbol = new Symbol(new Type(assignment.get("varType").equals("integer")? "int": assignment.get("varType"), assignment.get("isArray").equals("true")), assignment.get("variable"));
                    symbolTable.removeLocalVariable(methodNode.get("methodName"), symbol);
                }

                return isConst;
            }
        }

        for (JmmNode child: currentNode.getChildren()) {
            boolean res = checkAllInstances(child);
            isConst = isConst || res;
        }



        if (currentNode.getKind().equals("ReturnStmt")) {

            JmmNode methodNode = currentNode.getAncestor("MethodDeclaration").get();
            ArrayList<JmmNode> nodes = new ArrayList<>();

            nodes = getAllVariableDeclarations(methodNode, nodes);

            for (JmmNode node : nodes) {
                boolean used = dropDeadVars(node, methodNode);

                if(!used){
                    JmmNode assign = getVariableLastAssignment(methodNode, node.get("variableName"));
                    while (assign != null){
                        assign.delete();
                        assign = getVariableLastAssignment(methodNode, node.get("variableName"));
                    }
                    node.delete();
                }
            }

        }

        return isConst;
    }


    public ArrayList<JmmNode> getAllVariableDeclarations(JmmNode currentNode, ArrayList<JmmNode> nodes){
        if(currentNode.getKind().equals("VarDeclaration")){
            nodes.add(currentNode);
        }
        else if(currentNode.getKind().equals("ReturnStmt")){
            return nodes;
        }

        for (JmmNode child: currentNode.getChildren()) {
            getAllVariableDeclarations(child, nodes);
        }

        return nodes;
    }



    public boolean dropDeadVars(JmmNode node, JmmNode currentNode){

        boolean used = currentNode.getKind().equals("LiteralS") && currentNode.get("id").equals(node.get("variableName"));

        for (JmmNode child: currentNode.getChildren()) {
            used = used || dropDeadVars(node, child);
        }


        return used;
    }



    public boolean constantPropagationOptimization(){

        HashMap<JmmNode, Integer> variableAssignments = new HashMap<>();

        boolean alterations = false;
        // alterations = checkAllInstances(rootNode);

        /**/

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

        return alterations;
    }
}
