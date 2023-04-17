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
        addVisit("ReturnStmt", this::dealWithReturnStmt);
        addVisit("IfStatement", this::dealWithIfStatement);
    }

    SemanticAnalysis(){
        this.setDefaultValue(Collections::emptyList);
        this.setReduceSimple(this::joinReports);
        this.setDefaultVisit(this::visitDefault);
    }

    private List<Report> joinReports(List<Report> reps1, List<Report> reps2) {
        return SpecsCollections.concatList(reps1, reps2);
    }

    private List<String> getImports(SymbolTable symbolTable){
        List<String> imports = symbolTable.getImports(), result = new ArrayList<>();
        for (String importClass: imports){
            result.add(importClass);
            if(importClass.contains(".")){
                String[] modules = importClass.split("\\.");
                result.add(modules[modules.length-1]);
            }

        }
        return result;
    }

    private List<String> possibleVarTypes(SymbolTable symbolTable, Boolean includePrimitives){
        List<String> imports = getImports(symbolTable);
        imports.add(symbolTable.getClassName());
        if(includePrimitives){
            imports.add("int");
            imports.add("int[]");
            imports.add("integer");
            imports.add("boolean");
        }

        return imports;
    }

    private boolean isValidType(String nameType, SymbolTable symbolTable, Boolean includePrimitives){
        List<String> types = possibleVarTypes(symbolTable, includePrimitives);
        return types.contains(nameType);
    }

    private Type getVarType(JmmNode jmmNode){
        if(jmmNode.hasAttribute("isArray")){
            boolean isArray = jmmNode.get("isArray").equals("true");
            return new Type(jmmNode.get("varType"), isArray);
        }
        else{
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

    /** Returns a list with all the variables accessible in the scope of a function (variables declared inside said function + function parameters + class fields) */
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
                return new Type(s.getType().getName().equals("int")? "integer": s.getType().getName(), s.getType().isArray());
            }
        }
        return null;
    }

    /** Returns a boolean if number and type of the arguments matches the function method */
    private boolean checkMethodCallArguments(JmmNode jmmNode, SymbolTable symbolTable){

        if(symbolTable.getParameters(jmmNode.get("method")).size() != (jmmNode.getNumChildren()-1)){
            return false;
        }

        List<Symbol> functionParams = symbolTable.getParameters(jmmNode.get("method"));

        for (int i=0; i<functionParams.size(); i++) {
            Type typeOfParam = functionParams.get(i).getType();
            Type typeOfParamSanitized = new Type(typeOfParam.getName().equals("int")? "integer" : typeOfParam.getName(), typeOfParam.isArray()), typeOfArg = getVarType(jmmNode.getJmmChild(i+1));
            boolean equalType = typeOfArg.equals(typeOfParamSanitized);
            if(!equalType){return false;}
        }
        return true;
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
            boolean isImport = getImports(symbolTable).contains(jmmNode.get("id"));
            if(isImport){
                putType(jmmNode, new Type("library", false));
            }
            else{
                putType(jmmNode, new Type("undefined", false));
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+ jmmNode.get("id") +" does not exist."));
            }
        }
        System.out.println(reports);
        return reports;

    }

    private List<Report> dealWithBinaryOp(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        List<JmmNode> children = jmmNode.getChildren();
        String child1_type = children.get(0).get("varType"), child2_type = children.get(1).get("varType");
        boolean child1_isArray = getVarType(children.get(0)).isArray(), child2_isArray = getVarType(children.get(1)).isArray();

        boolean everythingOk;


        switch (jmmNode.get("op")){
            case "&&", "||":
                everythingOk = child1_type.equals(child2_type) && !((child1_type.equals("undefined")) || child1_type.equals("integer")) && !(child1_isArray || child2_isArray);
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

    private List<Report> dealWithMethodCall(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();
        JmmNode child = jmmNode.getJmmChild(0);
        boolean methodExists = symbolTable.getMethods().contains(jmmNode.get("method"));


        // If function caller is a 'this' object or an object with the class type
        if(child.getKind().equals("Object") || (child.hasAttribute("varType") && child.get("varType").equals(symbolTable.getClassName()))){
            if(methodExists && checkMethodCallArguments(jmmNode, symbolTable)){
                Type tp = symbolTable.getReturnType(jmmNode.get("method"));
                putType(jmmNode, tp);
            }

            // If class extends another Class
            else if(!symbolTable.getSuper().equals("")){
                putType(jmmNode, new Type("unknown", false));
            }

            // If class does not extend any other class and method isn't declared
            else{
                putType(jmmNode, new Type("undefined", false));
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Method "+jmmNode.get("method")+" ("+ (jmmNode.getNumChildren() - 1) +") couldn't be found."));
            }
        }

        else {
            boolean isValidType = isValidType(child.get("varType"), symbolTable, false);
            if(isValidType){
                putType(jmmNode, new Type("unknown", false));
            }
            else{
                putType(jmmNode, new Type("undefined", false));
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Method "+jmmNode.get("method")+" ("+ (jmmNode.getNumChildren() - 1) +") couldn't be found."));
            }
        }


        System.out.println(reports);
        return reports;

    }

    private List<Report> dealWithAssignment(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();
        JmmNode child1 = jmmNode.getJmmChild(0);

        // If it is a class field being declared and assigned outside a method
        if(getCallerFunctionName(jmmNode).equals("")){
            Type tp = matchVariable(symbolTable.getFields(), jmmNode.get("variable"));

            // If the assignment matches the variable type
            if(getVarType(child1).equals(tp)){
                putType(jmmNode, getVarType(child1));
            }
            else{
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+jmmNode.get("variable")+" is of type "+tp.getName()+"."));
                putType(jmmNode, new Type("undefined", false));
            }
        }

        else{
            List<Symbol> accessibleVars = getAccessibleVariables(getCallerFunctionName(jmmNode),symbolTable);
            Type tp = matchVariable(accessibleVars, jmmNode.get("variable"));
            // If variable does not exist:
            if(tp == null){
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+jmmNode.get("variable")+" couldn't be found."));
                putType(jmmNode, new Type("undefined", false));
            }
            // If variable does exist, it can be an array element assignment (2 children nodes) or a regular variable assignment (1 child node).
            else{
                // If it is a regular variable assignment, check if assigned value matches variable's varType.
                if(jmmNode.getNumChildren() == 1){
                    if(getVarType(child1).equals(tp)){
                        putType(jmmNode, tp);
                    }
                    else{
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+jmmNode.get("variable")+" is of type "+tp.getName()+"."));
                        putType(jmmNode, new Type("undefined", false));
                    }
                }
                // If it's an array element assignment, check if variable is of type int[], if array access is integer and if assigned value is integer.
                else{
                    if(!tp.isArray()){
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+jmmNode.get("variable")+" is of type "+tp.getName()+"."));
                        putType(jmmNode, new Type("undefined", false));
                    }
                    else if(!getVarType(child1).equals(new Type("integer", false))){
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Array access must be of type integer."));
                        putType(jmmNode, new Type("undefined", false));
                    }
                    else if(!getVarType(jmmNode.getJmmChild(1)).equals(new Type("integer", false))){
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Array element must be of type integer."));
                        putType(jmmNode, new Type("undefined", false));
                    }
                    else{
                        putType(jmmNode, new Type("integer", false));
                    }
                }
            }
        }

        System.out.println(reports);
        return reports;
    }

    private List<Report> dealWithObjectInstantiation(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        boolean validVar = isValidType(jmmNode.get("objectName"), symbolTable, false);
        if(validVar){
            putType(jmmNode, new Type(jmmNode.get("objectName"), false));
        }
        else{
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable type "+jmmNode.get("objectName")+" does not exist."));
            putType(jmmNode, new Type("undefined", false));
        }

        System.out.println(reports);
        return reports;
    }

    private List<Report> dealWithVarDeclaration(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        boolean isValidType = isValidType(jmmNode.getJmmChild(0).get("varType"), symbolTable, true);
        if(!isValidType){
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Type "+jmmNode.getJmmChild(0).get("varType")+" does not exist.");
            reports.add(rep);
        }
        else{
            Type tp = getVarType(jmmNode.getJmmChild(0));
            putType(jmmNode, tp);
        }

        System.out.println(reports);
        return reports;
    }

    private List<Report> dealWithClassVariable(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();
        String childId = jmmNode.getJmmChild(0).get("id"), childType = "";
        if(jmmNode.getJmmChild(0).hasAttribute("varType")){
            childType = jmmNode.getJmmChild(0).get("varType");
        }


        // If variable is of type equal to declared class or a 'this' object.
        if(childId.equals("this") || childType.equals(symbolTable.getClassName())){
            Type tp = matchVariable(symbolTable.getFields(), jmmNode.get("method"));
            // If variable is declared class field.
            if(tp != null){
                putType(jmmNode, tp);
            }
            // If variable not declared class field.
            else{
                // If class extends another class, any method call is allowed.
                if(symbolTable.getSuper().equals("")){
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Class field "+jmmNode.get("method")+" does not exist."));
                    putType(jmmNode, new Type("undefined", false));
                }
                else{
                    putType(jmmNode, new Type("unknown", false));
                }
            }
        }

        else {
            // Non-existent variable.
            if(childType.equals("undefined")){
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Can't read field of "+childId+", variable does not exist."));
                putType(jmmNode, new Type("undefined", false));

            }

            // Call to a library.
            else if (childType.equals("library")) {
                putType(jmmNode, new Type("unknown", false));
            }

            // Previously declared variable.
            else{
                // Type is not equal to already known primitive types.
                if(isValidType(childType, symbolTable, false)){
                    putType(jmmNode, new Type("unknown", false));
                }
                else{
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "No field "+jmmNode.get("method")+" associated with var of type "+childType+"."));
                    putType(jmmNode, new Type("undefined", false));
                }
            }
        }

        System.out.println(reports);
        return reports;
    }

    private List<Report> dealWithReturnStmt(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        Type statementType = getVarType(jmmNode.getJmmChild(0)), functionType = symbolTable.getReturnType(jmmNode.getJmmParent().get("methodName"));
        Type functionTypeSanitized = new Type(functionType.getName().equals("int")? "integer" : functionType.getName(), functionType.isArray());
        boolean returnCorrect = functionTypeSanitized.equals(statementType);
        if(!returnCorrect){
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Method "+jmmNode.getJmmParent().get("methodName")+" returns type "+functionTypeSanitized.getName()+" ."));
            putType(jmmNode, new Type("undefined", false));
        }
        else{
            putType(jmmNode, functionTypeSanitized);
        }

        System.out.println(reports);
        return reports;
    }

    private List<Report> dealWithIfStatement(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        String type = jmmNode.getJmmChild(0).getJmmChild(0).get("varType");
        if(!type.equals("boolean")){
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "If statement condition must be of type boolean."));
        }

        System.out.println(reports);
        return reports;
    }
}
