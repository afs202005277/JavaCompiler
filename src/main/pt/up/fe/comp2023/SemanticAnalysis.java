package pt.up.fe.comp2023;

import com.sun.tools.jconsole.JConsoleContext;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp.jmm.report.ReportType.ERROR;

public class SemanticAnalysis extends PostorderJmmVisitor<SymbolTable, List<Report>> {

    @Override
    protected void buildVisitor() {
        addVisit("MethodCall", this::dealWithMethodCall);
        addVisit("Parenthesis", this::dealWithParenthesis);
        addVisit("BinaryOp", this::dealWithBinaryOp);
        addVisit("UnaryOp", this::dealWithUnaryOp);
        addVisit("LiteralS", this::dealWithLiteralS);
        addVisit("ArrayIndex", this::dealWithArrayIndex);
        addVisit("Literal", this::dealWithLiteral);
        this.setDefaultVisit(this::visitDefault);
    }


    /* Returns the name of the function that calls a variable (given by JmmNode). If not a node with a MethodDeclaration ancestor returns empty string */
    private String getCallerFunctionName(JmmNode jmmNode){
        java.util.Optional<JmmNode> ancestor = jmmNode.getAncestor("MethodDeclaration");
        String functionName = "";
        if(ancestor.isPresent()){
            functionName = ancestor.get().get("methodName");
        }

        return functionName;
    }

    /* Returns a list with all the variables accessible in the scope of a function (variables declared inside said function + function parameters) */
    private List<Symbol> getFunctionVariables(String functionName, SymbolTable symbolTable){
        List<Symbol> localVars = symbolTable.table.get(functionName + "_variables");
        List<Symbol> functionParams = symbolTable.table.get(functionName + "_params");

        List<Symbol> functionVars = new ArrayList<>();

        if(functionParams != null){
            functionVars.addAll(functionParams);
        }

        if(localVars != null){
            functionVars.addAll(localVars);
        }

        return functionVars;
    }

    /* Receives a list of symbols and checks for the occurrence of string name ID */
    private Type matchVariable(List<Symbol> list, String id){
        for (Symbol s: list) {
            if(s.getName().equals(id)){
                return s.getType();
            }
        }
        return null;
    }


    private List<Report> visitDefault(JmmNode jmmNode, SymbolTable symbolTable) {
        System.out.println(jmmNode.getKind());
        List<Symbol> sm = symbolTable.getLocalVariables("computeFac");
        java.util.Optional<JmmNode> ancestor = jmmNode.getAncestor("MethodDeclaration");
        String functionName = "";
        if(ancestor.isPresent()){
            functionName = ancestor.get().getKind();
        }
        return new ArrayList<>();
    }


    private List<Report> dealWithArrayIndex(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        // verificar se children[0] existe, antes de proceder
        if(jmmNode.getChildren().get(0).get("varType").equals("undefined")){
            jmmNode.put("varType", "undefined");
            return reports;
        }

        // verificar que a variavel é um array
        if(!jmmNode.getChildren().get(0).get("isArray").equals("true")){
            jmmNode.put("varType", "undefined");
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable " + jmmNode.getChildren().get(0).get("id") + " is not of type array");
            reports.add(rep);
        }

        // verificar que o acesso ao array é do tipo int
        else if(!jmmNode.getChildren().get(1).get("varType").equals("integer") && !jmmNode.getChildren().get(1).get("varType").equals("undefined")){
            // FIXME quando usada uma variavel que nao existe verificar se é melhor ficar com tipo undefined ou tipo int
            jmmNode.put("varType", "undefined");
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Array access must be of type Integer");
            reports.add(rep);
        }

        else{
            jmmNode.put("varType", jmmNode.getChildren().get(0).get("varType"));
            jmmNode.put("isArray", "false");
        }



        System.out.println(reports);
        return reports;
    }

    private List<Report> dealWithLiteralS(JmmNode jmmNode, SymbolTable symbolTable) {

        List<Report> reports = new ArrayList<>();

        String name = jmmNode.get("id");

        String functionName = this.getCallerFunctionName(jmmNode);

        List<Symbol> functionVars = getFunctionVariables(functionName, symbolTable);

        Type varType = matchVariable(functionVars, name);

        if(varType == null && (!jmmNode.getJmmParent().getKind().equals("MethodCall"))){
            jmmNode.put("varType", "undefined");
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+ name+" can't be found.");
            reports.add(rep);
        }
        else if(varType != null && (!jmmNode.getJmmParent().getKind().equals("MethodCall"))){
            jmmNode.put("varType", varType.getName().equals("int")? "integer" : "bool");
            jmmNode.put("isArray", String.valueOf(varType.isArray()));
        }

        else{
            jmmNode.put("varType", "libraryMethodInvocation");
            jmmNode.put("isArray", "false");
        }

        System.out.println(reports);
        return reports;

    }

    private List<Report> dealWithBinaryOp(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        List<JmmNode> children = jmmNode.getChildren();
        boolean everythingOk = true;

        switch (jmmNode.get("op")){
            case "&&", "||":
                everythingOk = children.get(0).get("varType").equals(children.get(1).get("varType")) && children.get(0).get("varType").equals("boolean");
                if(!everythingOk){
                    Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Binary operator " +jmmNode.get("op") +" expects two booleans.");
                    reports.add(rep);
                }
                break;

            case "!=", "==", "<" , ">" , "<=" , ">=":
                everythingOk = children.get(0).get("varType").equals(children.get(1).get("varType")) && !children.get(0).get("varType").equals("undefined");
                if(!everythingOk){
                    Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Binary operator " +jmmNode.get("op") +" expects two variables of the same type.");
                    reports.add(rep);
                }
                break;

            case "*", "/", "+", "-":
                everythingOk = children.get(0).get("varType").equals(children.get(1).get("varType")) && children.get(0).get("varType").equals("integer");
                if(!everythingOk){
                    Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Binary operator " +jmmNode.get("op") +" expects two integers.");
                    reports.add(rep);
                }
                break;

            default:
                everythingOk = false;
                Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Binary operation incorrect.");
                reports.add(rep);
                break;
        }

        if(!everythingOk) {
            jmmNode.put("varType", "undefined");
        }

        else{ jmmNode.put("varType", children.get(0).get("varType")); }
        System.out.println(reports);
        return reports;
    }

    private List<Report> dealWithLiteral(JmmNode jmmNode, SymbolTable symbolTable) {

        boolean isInteger = jmmNode.hasAttribute("integer");
        if(isInteger){
            jmmNode.put("varType", "integer");
        }
        else{
            jmmNode.put("varType", "boolean");
        }
        return new ArrayList<>();
    }

    private List<Report> dealWithParenthesis(JmmNode jmmNode, SymbolTable symbolTable) {
        jmmNode.put("varType", jmmNode.getChildren().get(0).get("varType"));
        return new ArrayList<>();
    }

    private List<Report> dealWithMethodCall(JmmNode jmmNode, SymbolTable symbolTable) {
        try{
            Type tp = symbolTable.getReturnType(jmmNode.get("method"));
            jmmNode.put("varType", tp.getName().equals("int")? "integer" : tp.getName());
            jmmNode.put("isArray", String.valueOf(tp.isArray()));
        }
        catch (Exception e){
            jmmNode.put("varType","libraryMethodInvocation");
            jmmNode.put("isArray", "false");
        }


        return new ArrayList<>();
    }

    private List<Report> dealWithUnaryOp(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();
        if(!jmmNode.getChildren().get(0).get("varType").equals("boolean")){
            jmmNode.put("varType", "undefined");
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Unary operator ! must be used on variable of type boolean.");
            reports.add(rep);
        }
        jmmNode.put("varType", "boolean");
        jmmNode.put("isArray", "false");
        System.out.println(reports);
        return reports;
    }


}
