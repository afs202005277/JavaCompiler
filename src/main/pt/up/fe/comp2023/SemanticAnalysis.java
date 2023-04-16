package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.specs.util.SpecsCollections;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    private void putType(JmmNode jmmNode, Type tp){
        jmmNode.put("varType", tp.getName().equals("int")? "integer": tp.getName());
        jmmNode.put("isArray", Boolean.toString(tp.isArray()));
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
    private List<Symbol> getAccessibleVariables(String functionName, SymbolTable symbolTable){
        List<Symbol> localVars = symbolTable.table.get(functionName + "_variables");
        List<Symbol> functionParams = symbolTable.table.get(functionName + "_params");
        List<Symbol> classFields = symbolTable.getFields();
        List<Symbol> functionVars = new ArrayList<>();

        if(functionParams != null){
            functionVars.addAll(functionParams);
        }

        if(localVars != null){
            functionVars.addAll(localVars);
        }

        if(classFields != null){
            functionVars.addAll(classFields);
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


    private List<Report> dealWithArrayIndex(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        JmmNode child1 = jmmNode.getJmmChild(0), child2 = jmmNode.getJmmChild(1);

        if(child1.get("varType").equals("integer") && child1.get("isArray").equals("true")){
            if(child2.get("varType").equals("integer") && child2.get("isArray").equals("false")){
                putType(jmmNode, new Type("integer", false));
            }
            else{
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Array access must be of type Integer."));
                putType(jmmNode, new Type("undefined", false));
            }
        }

        else {
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+child1.get("id")+" not of type integer array."));
            putType(jmmNode, new Type("undefined", false));
        }


        System.out.println(reports);
        return reports;
    }

    private List<Report> dealWithLiteralS(JmmNode jmmNode, SymbolTable symbolTable) {

        List<Report> reports = new ArrayList<>();
        Type variable = matchVariable(getAccessibleVariables(getCallerFunctionName(jmmNode), symbolTable), jmmNode.get("id"));

        if(variable!=null){
            putType(jmmNode, variable);
        }

        else{
            putType(jmmNode, new Type("undefined", false));
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+ jmmNode.get("id") +" does not exist."));
        }
        System.out.println(reports);
        return reports;

    }

    private List<Report> dealWithBinaryOp(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        List<JmmNode> children = jmmNode.getChildren();
        String child1_type = children.get(0).get("varType"), child2_type = children.get(1).get("varType");
        boolean child1_isArray = Boolean.getBoolean(children.get(0).get("isArray")), child2_isArray = Boolean.getBoolean(children.get(0).get("isArray"));

        boolean everythingOk;


        switch (jmmNode.get("op")){
            case "&&", "||":
                everythingOk = child1_type.equals(child2_type) && !(child1_type.equals("undefined") || child1_type.equals("integer")) && !(child1_isArray || child2_isArray);
                if(!everythingOk){
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Binary operator " +jmmNode.get("op") +" not defined for given type."));
                }
                break;

            case "!=", "==":
                everythingOk = child1_type.equals(child2_type) && !(child1_type.equals("undefined")) && !(child1_isArray || child2_isArray);
                if(!everythingOk){
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Binary operator " +jmmNode.get("op") +" expects two non-null variables of the same type."));
                }
                break;

            case "*", "/", "+", "-", "<" , ">" , "<=" , ">=":
                everythingOk = child1_type.equals(child2_type) && !(child1_type.equals("undefined") || child1_type.equals("boolean")) && !(child1_isArray || child2_isArray);
                if(!everythingOk){
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Binary operator " +jmmNode.get("op") +" not defined for given type."));
                }
                break;

            default:
                everythingOk = false;
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Binary operation incorrect."));
                break;
        }

        if(!everythingOk) {
            putType(jmmNode, new Type("undefined", false));
        }

        else{
            putType(jmmNode, new Type(child1_type, false));
        }

        System.out.println(reports);
        return reports;
    }

    private List<Report> dealWithLiteral(JmmNode jmmNode, SymbolTable symbolTable) {

        boolean isInteger = jmmNode.hasAttribute("integer");
        if(isInteger){
            putType(jmmNode, new Type("integer", false));
        }
        else{
            putType(jmmNode, new Type("boolean", false));
        }
        return new ArrayList<>();
    }

    private List<Report> dealWithParenthesis(JmmNode jmmNode, SymbolTable symbolTable) {
        putType(jmmNode, new Type(jmmNode.getJmmChild(0).get("varType"), Boolean.getBoolean(jmmNode.getJmmChild(0).get("isArray"))));
        return new ArrayList<>();
    }

    private List<Report> dealWithUnaryOp(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        if(!jmmNode.getJmmChild(0).get("varType").equals("boolean")){
            putType(jmmNode, new Type("undefined", false));
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Unary operator ! must be used on variable of type boolean."));
        }
        else{
            putType(jmmNode, new Type("boolean", false));
        }

        System.out.println(reports);
        return reports;
    }

    private List<Report> dealWithWhileLoop(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();
        if(!jmmNode.getJmmChild(0).get("varType").equals("boolean")){
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "While loop condition must be of type boolean."));
        }

        System.out.println(reports);
        return reports;
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

    //fixme
    private List<Report> dealWithAssignment(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        if(jmmNode.getJmmChild(0).get("varType").equals("libraryMethodInvocation")){return reports;}

        boolean ancestorExists = jmmNode.getAncestor("MethodDeclaration").isPresent();
        Type tp;

        if(ancestorExists){
            String functionName = jmmNode.getAncestor("MethodDeclaration").get().get("methodName");
            tp = matchVariable(getAccessibleVariables(functionName, symbolTable), jmmNode.get("variable"));
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

    //fixme
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


    /** FIXME - tenho primeiro de verificar se a variavel em questao existe. pode ser um objeto, uma library ou um this */
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
