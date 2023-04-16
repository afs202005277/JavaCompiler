package pt.up.fe.comp2023;

import com.sun.tools.jconsole.JConsoleContext;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.specs.util.SpecsCollections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
        addVisit("WhileLoop", this::dealWithWhileLoop);
        addVisit("Assignment", this::dealWithAssignment);
        addVisit("ObjectInstantiation", this::dealWithObjectInstantiation);
        addVisit("VarDeclaration", this::dealWithVarDeclaration);
        addVisit("ClassVariable", this::dealWithClassVariable);
    }


    SemanticAnalysis(){
        this.setDefaultValue(Collections::emptyList);
        this.setReduceSimple(this::joinReports);
        this.setDefaultVisit(this::visitDefault);
    }

    private List<Report> joinReports(List<Report> reps1, List<Report> reps2) {
        return SpecsCollections.concatList(reps1, reps2);
    }

    private List<String> possibleVarTypes(SymbolTable symbolTable){
        List<String> imports = symbolTable.getImports();
        imports.add(symbolTable.getClassName());
        imports.add("int");
        imports.add("int[]");
        imports.add("boolean");
        return imports;
    }

    private boolean isValidType(String nameType, SymbolTable symbolTable){
        List<String> types = possibleVarTypes(symbolTable);
        for(String type: types){
            if(type.equals(nameType)){ return true; }
        }
        return false;
    }

    private Type matchVarType(JmmNode jmmNode){
        if(jmmNode.get("varType").equals("int")){
            return new Type("integer", false);
        }
        else if(jmmNode.get("varType").equals("int[]")){
            return new Type("integer", true);
        }
        else {
            return new Type(jmmNode.get("varType"), false);
        }
    }


    /** Returns the name of the function that calls a variable (given by JmmNode). If not a node with a MethodDeclaration ancestor returns empty string */
    private String getCallerFunctionName(JmmNode jmmNode){
        java.util.Optional<JmmNode> ancestor = jmmNode.getAncestor("MethodDeclaration");
        String functionName = "";
        if(ancestor.isPresent()){
            functionName = ancestor.get().get("methodName");
        }

        return functionName;
    }

    /** Returns a list with all the variables accessible in the scope of a function (variables declared inside said function + function parameters) */
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

    /** Receives a list of symbols and checks for the occurrence of string name ID */
    private Type matchVariable(List<Symbol> list, String id){
        for (Symbol s: list) {
            if(s.getName().equals(id)){
                Type tp = new Type(s.getType().getName().equals("int")? "integer": s.getType().getName(), s.getType().isArray());
                return tp;
            }
        }
        return null;
    }


    private List<Report> visitDefault(JmmNode jmmNode, SymbolTable symbolTable) {
        System.out.println(jmmNode.getKind());
        return new ArrayList<>();
    }


    // fixme
    private List<Report> dealWithArrayIndex(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        // verificar se children[0] existe, antes de proceder
        if(jmmNode.getJmmChild(0).get("varType").equals("undefined")){
            jmmNode.put("varType", "undefined");
            return reports;
        }

        // verificar que a variavel é um array
        if(!jmmNode.getJmmChild(0).get("isArray").equals("true")){
            jmmNode.put("varType", "undefined");
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable " + jmmNode.getJmmChild(0).get("id") + " is not of type array");
            reports.add(rep);
        }

        // verificar que o acesso ao array é do tipo int
        else if(!jmmNode.getJmmChild(1).get("varType").equals("integer") && !jmmNode.getJmmChild(1).get("varType").equals("undefined")){
            // FIXME quando usada uma variavel que nao existe verificar se é melhor ficar com tipo undefined ou tipo int
            jmmNode.put("varType", "undefined");
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Array access must be of type Integer");
            reports.add(rep);
        }

        else{
            jmmNode.put("varType", jmmNode.getJmmChild(0).get("varType"));
            jmmNode.put("isArray", "false");
        }



        System.out.println(reports);
        return reports;
    }

    // fixme
    private List<Report> dealWithLiteralS(JmmNode jmmNode, SymbolTable symbolTable) {

        List<Report> reports = new ArrayList<>();

        String name = jmmNode.get("id");

        String functionName = this.getCallerFunctionName(jmmNode);

        List<Symbol> functionVars = getFunctionVariables(functionName, symbolTable);

        Type varType = matchVariable(functionVars, name);

        /*if(varType == null && (!jmmNode.getJmmParent().getKind().equals("MethodCall"))){
            jmmNode.put("varType", "undefined");
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+ name+" can't be found.");
            reports.add(rep);
        }
        else if(varType != null && (!jmmNode.getJmmParent().getKind().equals("MethodCall"))){
            jmmNode.put("varType", varType.getName());
            jmmNode.put("isArray", String.valueOf(varType.isArray()));
        }

        else{
            jmmNode.put("varType", "libraryMethodInvocation");
            jmmNode.put("isArray", "false");
        }*/

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
                everythingOk = children.get(0).get("varType").equals(children.get(1).get("varType")) && children.get(0).get("varType").equals("integer") && children.get(0).get("isArray").equals("false") && children.get(1).get("isArray").equals("false");
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
            jmmNode.put("isArray", "false");
        }
        else{
            jmmNode.put("varType", "boolean");
            jmmNode.put("isArray", "false");
        }
        return new ArrayList<>();
    }

    private List<Report> dealWithParenthesis(JmmNode jmmNode, SymbolTable symbolTable) {
        jmmNode.put("varType", jmmNode.getJmmChild(0).get("varType"));
        return new ArrayList<>();
    }

    // fixme
    private List<Report> dealWithMethodCall(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();
        if(jmmNode.getJmmChild(0).hasAttribute("id") && jmmNode.getJmmChild(0).get("id").equals("this")){
            try{
                Type tp = symbolTable.getReturnType(jmmNode.get("method"));
                int num_of_params = symbolTable.getParameters(jmmNode.get("method")).size();
                if(num_of_params != (jmmNode.getNumChildren()-1)){ throw new Exception(); }
                jmmNode.put("varType", tp.getName());
                jmmNode.put("isArray", String.valueOf(tp.isArray()));
            }
            catch (Exception e){
                jmmNode.put("varType","undefined");
                Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Method " + jmmNode.get("method") + "("+ (jmmNode.getNumChildren()-1) + ") does not exist.");
                reports.add(rep);
            }
        }

        else {
            jmmNode.put("varType", "libraryMethodInvocation");
            jmmNode.put("isArray", "false");
        }

        System.out.println(reports);
        return reports;
    }

    private List<Report> dealWithUnaryOp(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();
        if(!jmmNode.getJmmChild(0).get("varType").equals("boolean")){
            jmmNode.put("varType", "undefined");
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Unary operator ! must be used on variable of type boolean.");
            reports.add(rep);
        }
        jmmNode.put("varType", "boolean");
        jmmNode.put("isArray", "false");
        System.out.println(reports);
        return reports;
    }


    private List<Report> dealWithWhileLoop(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();
        if(!jmmNode.getJmmChild(0).get("varType").equals("boolean")){
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "While loop condition must be of type boolean.");
            reports.add(rep);
        }

        System.out.println(reports);
        return reports;
    }

    private List<Report> dealWithAssignment(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        if(jmmNode.getJmmChild(0).get("varType").equals("libraryMethodInvocation")){return reports;}

        boolean ancestorExists = jmmNode.getAncestor("MethodDeclaration").isPresent();
        Type tp;

        if(ancestorExists){
            String functionName = jmmNode.getAncestor("MethodDeclaration").get().get("methodName");
            tp = matchVariable(getFunctionVariables(functionName, symbolTable), jmmNode.get("variable"));
        }

        else{
            tp = matchVariable(symbolTable.getFields(), jmmNode.get("variable"));
        }


        if((jmmNode.getNumChildren() == 1 && !tp.getName().equals(jmmNode.getJmmChild(0).get("varType"))) || (jmmNode.getNumChildren() != 1 && (!tp.isArray()))){
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+jmmNode.get("variable")+" is of type "+ tp.getName());
            reports.add(rep);
        }

        else if(jmmNode.getNumChildren() == 1 && tp.isArray()){
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+jmmNode.get("variable")+" is an array.");
            reports.add(rep);
        }

        else if(jmmNode.getNumChildren() != 1 && !jmmNode.getJmmChild(0).get("varType").equals("integer")){
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Error in array access.");
            reports.add(rep);
        }

        else if(jmmNode.getNumChildren() != 1 && !jmmNode.getJmmChild(1).get("varType").equals("integer")) {
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Assignment of array element must be an integer.");
            reports.add(rep);
        }

        System.out.println(reports);
        return reports;
    }


    // fixme - tem de verificar se o nome do construtor coincide com o tipo
    private List<Report> dealWithObjectInstantiation(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        jmmNode.put("varType", "libraryMethodInvocation");
        jmmNode.put("isArray", "false");

        System.out.println(reports);
        return reports;
    }

    /** Only verifies if the type of the declared variable is valid. */
    private List<Report> dealWithVarDeclaration(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        boolean isValidType = isValidType(jmmNode.getJmmChild(0).get("varType"), symbolTable);
        if(!isValidType){
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Type "+jmmNode.getJmmChild(0).get("varType")+" does not exist.");
            reports.add(rep);
        }
        else{
            Type tp = matchVarType(jmmNode.getJmmChild(0));
            jmmNode.put("varType", tp.getName());
            jmmNode.put("varType", Boolean.toString(tp.isArray()));
        }

        System.out.println(reports);
        return reports;
    }


    /** TODO - tenho primeiro de verificar se a variavel em questao existe. pode ser um objeto, uma library ou um this */
    private List<Report> dealWithClassVariable(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        /*if(jmmNode.getJmmChild(0).get("id").equals("this")){
            Type tp = matchVariable(symbolTable.getFields(), jmmNode.get("method"));
            if(tp != null){
                jmmNode.put("varType", tp.getName());
                jmmNode.put("isArray", Boolean.toString(tp.isArray()));
            }
            else{
                Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Class field "+jmmNode.get("method")+" does not exist.");
                reports.add(rep);
            }
        }

        else{

        }*/

        System.out.println(reports);
        return reports;
    }
}
