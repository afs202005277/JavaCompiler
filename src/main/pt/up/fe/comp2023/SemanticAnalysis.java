package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.specs.util.SpecsCollections;

import java.util.*;

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
        addVisit("Stmt", this::dealWithStmt);
        addVisit("Object", this::dealWithObject);
        addVisit("ArrayDeclaration", this::dealWithArray);
        addVisit("AssignmentArray", this::dealWithArray);
        addVisit("Length", this::dealWithLength);
    }

    SemanticAnalysis(){
        this.setDefaultValue(Collections::emptyList);
        this.setReduceSimple(this::joinReports);
        this.setDefaultVisit(this::visitDefault);
    }

    private List<Report> joinReports(List<Report> reps1, List<Report> reps2) {
        return SpecsCollections.concatList(reps1, reps2);
    }

    private boolean methodExists(String methodName, SymbolTable symbolTable){
        List<String> methods = symbolTable.getMethods();
        for(String method: methods){
            if(method.contains(methodName)){
                return true;
            }
        }
        return false;
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
        imports.add("unknown");
        imports.add("library");
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
        if(jmmNode.hasAttribute("isArray") && jmmNode.hasAttribute("varType")){
            boolean isArray = jmmNode.get("isArray").equals("true");
            return new Type(jmmNode.get("varType"), isArray);
        }
        else if(jmmNode.hasAttribute("varType")){
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
        else {
            return new Type(jmmNode.get("undefined"), false);
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

    /** Returns whether two variables have equivalent type. Child class equivalent to parent class  */
    private boolean equalTypes(Type tp1, Type tp2, SymbolTable symbolTable){
        String superClass = symbolTable.getSuper();
        Type intArray = new Type("integer", true);

        if(tp1.equals(intArray) || tp2.equals(intArray)){
            return tp1.equals(tp2);
        }

        else if((getImports(symbolTable).contains(tp1.getName()) && getImports(symbolTable).contains(tp2.getName())) || tp1.getName().equals("unknown") || tp2.getName().equals("unknown")){
            return true;
        }

        else if(superClass.equals("")){
            return tp1.equals(tp2);
        }

        else{
            if((tp1.getName().equals(symbolTable.getClassName()) && tp2.getName().equals(superClass)) || (tp2.getName().equals(symbolTable.getClassName()) && tp1.getName().equals(superClass))){
                return tp2.isArray() == tp1.isArray();
            }
            else{
                return tp1.equals(tp2);
            }
        }
    }

    /** Returns a list with all the variables accessible in the scope of a function (variables declared inside said function + function parameters + class fields) */
    private List<Symbol> getAccessibleVariables(String functionName, SymbolTable symbolTable){

        List<Symbol> localVars = symbolTable.table.get(functionName + "_variables");
        List<Symbol> functionParams = symbolTable.table.get(functionName + "_params");
        List<Symbol> classFields = new ArrayList<>(symbolTable.getFields());
        List<Symbol> functionVars = new ArrayList<>();

        List<Symbol> symbols = symbolTable.getSomethingFromTable("methods");

        for (Symbol symbol: symbols) {
            if(symbol.getName().contains(functionName)){
                if(symbol.getName().contains("static")){
                    classFields.clear();
                    break;
                }
            }
        }

        if(functionParams != null){
            functionVars.addAll(functionParams);
        }

        if(localVars != null){
            functionVars.addAll(localVars);
        }

        functionVars.addAll(classFields);

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

        if(!jmmNode.hasAttribute("method")){
            return false;
        }

        else if(symbolTable.getParameters(jmmNode.get("method")).size() != (jmmNode.getNumChildren()-1)){
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
        return new ArrayList<>();
    }

    private List<Report> dealWithArrayIndex(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        JmmNode child1 = jmmNode.getJmmChild(0), child2 = jmmNode.getJmmChild(1);

        if(!child1.hasAttribute("varType") || !child1.hasAttribute("isArray") || !child2.hasAttribute("varType") || !child2.hasAttribute("isArray")){
            //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable type couldn't be found."));
            putType(jmmNode, new Type("undefined", false));
        }

        else if(child1.get("varType").equals("integer") && child1.get("isArray").equals("true")){
            if(child2.get("varType").equals("integer") && child2.get("isArray").equals("false")){
                putType(jmmNode, new Type("integer", false));
            }
            else{
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Array access must be of type Integer."));
                putType(jmmNode, new Type("undefined", false));
            }
        }

        else {
            if(!child1.hasAttribute("id")){
                //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable not of type integer array."));
            }
            else {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+child1.get("id")+" not of type integer array."));
            }
            putType(jmmNode, new Type("undefined", false));
        }


        
        return reports;
    }

    private List<Report> dealWithLiteralS(JmmNode jmmNode, SymbolTable symbolTable) {

        List<Report> reports = new ArrayList<>();

        if(!jmmNode.hasAttribute("id")){
            //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable ID couldn't be found."));
            putType(jmmNode, new Type("undefined", false));
            return reports;
        }

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
        
        return reports;

    }

    private List<Report> dealWithBinaryOp(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        List<JmmNode> children = jmmNode.getChildren();
        if(!(children.get(0).hasAttribute("varType") && children.get(0).hasAttribute("isArray") && children.get(1).hasAttribute("varType") && children.get(1).hasAttribute("isArray"))){
            //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable type couldn't be found."));
            putType(jmmNode, new Type("undefined", false));
            return reports;
        }

        String child1_type = children.get(0).get("varType"), child2_type = children.get(1).get("varType");
        boolean child1_isArray = getVarType(children.get(0)).isArray(), child2_isArray = getVarType(children.get(1)).isArray();

        Type child1Type = new Type(child1_type, child1_isArray), child2Type = new Type(child2_type, child2_isArray);

        boolean everythingOk;

        List<String> boolOp = Arrays.asList("&&", "||", "!=", "==", "<" , ">" , "<=" , ">=");

        switch (jmmNode.get("op")){
            case "&&", "||":
                everythingOk = equalTypes(child1Type, child2Type, symbolTable) && !((child1_type.equals("undefined")) || child1_type.equals("integer")) && !(child1_isArray || child2_isArray);
                if(!everythingOk){
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Binary operator " +jmmNode.get("op") +" not defined for given type."));
                }
                break;

            case "!=", "==":
                everythingOk = equalTypes(child1Type, child2Type, symbolTable) && !(child1_type.equals("undefined")) && !(child1_isArray || child2_isArray);
                if(!everythingOk){
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Binary operator " +jmmNode.get("op") +" expects two non-null variables of the same type."));
                }
                break;

            case "*", "/", "+", "-", "<" , ">" , "<=" , ">=":
                everythingOk = equalTypes(child1Type, child2Type, symbolTable) && !(child1_type.equals("undefined") || child1_type.equals("boolean")) && !(child1_isArray || child2_isArray);
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
        else if(boolOp.contains(jmmNode.get("op"))) {
            putType(jmmNode, new Type("boolean",false));
        }
        else{
            putType(jmmNode, new Type("integer",false));
        }

        
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
        List<Report> reports = new ArrayList<>();

        if(!(jmmNode.getJmmChild(0).hasAttribute("varType") && jmmNode.getJmmChild(0).hasAttribute("isArray"))){
            //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable type couldn't be found."));
            putType(jmmNode, new Type("undefined", false));
            return reports;
        }

        putType(jmmNode, new Type(jmmNode.getJmmChild(0).get("varType"), Boolean.getBoolean(jmmNode.getJmmChild(0).get("isArray"))));
        return reports;
    }

    private List<Report> dealWithUnaryOp(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        if(!jmmNode.getJmmChild(0).hasAttribute("varType")){
            //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable type couldn't be found."));
            putType(jmmNode, new Type("undefined", false));
            return reports;
        }

        else if(!(jmmNode.getJmmChild(0).get("varType").equals("boolean") || jmmNode.getJmmChild(0).get("varType").equals("unknown"))){
            putType(jmmNode, new Type("undefined", false));
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Unary operator ! must be used on variable of type boolean."));
        }
        else{
            putType(jmmNode, new Type("boolean", false));
        }

        
        return reports;
    }

    private List<Report> dealWithWhileLoop(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        if(!(jmmNode.getJmmChild(0).hasAttribute("varType") && jmmNode.getJmmChild(0).hasAttribute("isArray"))){
            //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable type couldn't be found."));
            putType(jmmNode, new Type("undefined", false));
            return reports;
        }


        if(!(jmmNode.getJmmChild(0).get("varType").equals("boolean") || jmmNode.getJmmChild(0).get("varType").equals("unknown")) || (jmmNode.getJmmChild(0).get("isArray").equals("true"))){
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "While loop condition must be of type boolean."));
        }

        
        return reports;
    }

    private List<Report> dealWithMethodCall(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();
        JmmNode child = jmmNode.getJmmChild(0);

        if(!jmmNode.hasAttribute("method")){
            //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "No method attribute was found."));
            putType(jmmNode, new Type("undefined", false));
            return reports;
        }


        boolean methodExists = methodExists(jmmNode.get("method"), symbolTable);

        // If function is static, can't invoke 'this' keyword
        if(child.getKind().equals("Object") && jmmNode.getAncestor("MethodDeclaration").isPresent() && jmmNode.getAncestor("MethodDeclaration").get().hasAttribute("isStatic") && jmmNode.getAncestor("MethodDeclaration").get().get("isStatic").equals("static")){
            putType(jmmNode, new Type("undefined", false));
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Method "+jmmNode.getAncestor("MethodDeclaration").get().get("methodName")+" is static, can't use 'this' keyword."));
        }

        // If function caller is a 'this' object or an object with the class type
        else if(child.getKind().equals("Object") || (child.hasAttribute("varType") && child.get("varType").equals(symbolTable.getClassName()))){
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

            if(!child.hasAttribute("varType")){
                //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable type couldn't be found."));
                putType(jmmNode, new Type("undefined", false));
                return reports;
            }

            boolean isValidType = isValidType(child.get("varType"), symbolTable, false);
            if(isValidType){
                putType(jmmNode, new Type("unknown", false));
            }
            else{
                putType(jmmNode, new Type("undefined", false));
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Method "+jmmNode.get("method")+" ("+ (jmmNode.getNumChildren() - 1) +") couldn't be found."));
            }
        }


        
        return reports;

    }

    private List<Report> dealWithAssignment(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();
        JmmNode child1 = jmmNode.getJmmChild(0);

        // If it is a class field being declared and assigned outside a method
        if(getCallerFunctionName(jmmNode).equals("")){
            String id;
            if(jmmNode.hasAttribute("id")){
                id = jmmNode.get("id");
            }
            else { id = jmmNode.get("variable");}

            Type tp = matchVariable(symbolTable.getFields(), id);

            // If the assignment matches the variable type
            if(equalTypes(getVarType(child1), tp, symbolTable)){
                putType(jmmNode, getVarType(child1));
            }
            else{
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+id+" is of type "+tp.getName()+"."));
                putType(jmmNode, new Type("undefined", false));
            }
        }

        else{
            List<Symbol> accessibleVars = getAccessibleVariables(getCallerFunctionName(jmmNode),symbolTable);
            String id;
            if(jmmNode.hasAttribute("id")){
                id = jmmNode.get("id");
            }
            else { id = jmmNode.get("variable");}

            Type tp = matchVariable(accessibleVars, id);
            // If variable does not exist:
            if(tp == null){
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+id+" couldn't be found."));
                putType(jmmNode, new Type("undefined", false));
            }
            // If variable does exist, it can be an array element assignment (2 children nodes) or a regular variable assignment (1 child node).
            else{
                // If it is a regular variable assignment, check if assigned value matches variable's varType.
                if(jmmNode.getNumChildren() == 1){
                    if(equalTypes(getVarType(child1), tp, symbolTable)){
                        putType(jmmNode, tp);
                    }
                    else{
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+id+" is of type "+tp.getName()+"."));
                        putType(jmmNode, new Type("undefined", false));
                    }
                }
                // If it's an array element assignment, check if variable is of type int[], if array access is integer and if assigned value is integer.
                else{
                    if(!tp.isArray()){
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+id+" is of type "+tp.getName()+"."));
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

        
        return reports;
    }

    private List<Report> dealWithObjectInstantiation(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        if(!jmmNode.hasAttribute("objectName")){
            //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Object name couldn't be found"));
            putType(jmmNode, new Type("undefined", false));
            return reports;
        }

        boolean validVar = isValidType(jmmNode.get("objectName"), symbolTable, false);
        if(validVar){
            putType(jmmNode, new Type(jmmNode.get("objectName"), false));
        }
        else{
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable type "+jmmNode.get("objectName")+" does not exist."));
            putType(jmmNode, new Type("undefined", false));
        }

        
        return reports;
    }

    private List<Report> dealWithVarDeclaration(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        if(!jmmNode.getJmmChild(0).hasAttribute("varType")){
            //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable type couldn't be found"));
            putType(jmmNode, new Type("undefined", false));
            return reports;
        }
        boolean isValidType = isValidType(jmmNode.getJmmChild(0).get("varType"), symbolTable, true);
        if(!isValidType){
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Type "+jmmNode.getJmmChild(0).get("varType")+" does not exist.");
            reports.add(rep);
        }
        else{
            Type tp = getVarType(jmmNode.getJmmChild(0));
            putType(jmmNode, tp);
        }

        
        return reports;
    }

    private List<Report> dealWithClassVariable(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        if(!jmmNode.getJmmChild(0).hasAttribute("id")){
            //putType(jmmNode, new Type("undefined", false));
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Class variable couldn't be found."));
            return reports;
        }

        String childId = jmmNode.getJmmChild(0).get("id"), childType = "";
        if(jmmNode.getJmmChild(0).hasAttribute("varType")){
            childType = jmmNode.getJmmChild(0).get("varType");
        }

        // If method is static, can't access 'this' keyword.
        if(jmmNode.getJmmChild(0).getKind().equals("Object") && jmmNode.getAncestor("MethodDeclaration").isPresent() && jmmNode.getAncestor("MethodDeclaration").get().hasAttribute("isStatic") && jmmNode.getAncestor("MethodDeclaration").get().get("isStatic").equals("static")){
            putType(jmmNode, new Type("undefined", false));
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Method "+jmmNode.getAncestor("MethodDeclaration").get().get("methodName")+" is static, can't use 'this' keyword."));
        }

        // If variable is of type equal to declared class or a 'this' object.
        else if(childId.equals("this") || childType.equals(symbolTable.getClassName())){
            if(!jmmNode.hasAttribute("method")){
                putType(jmmNode, new Type("undefined", false));
                //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Class field couldn't be found"));
                return reports;
            }
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

        
        return reports;
    }

    private List<Report> dealWithReturnStmt(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        if(!jmmNode.getJmmParent().hasAttribute("methodName")){
            //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Method name couldn't be found."));
            putType(jmmNode, new Type("undefined", false));
            return reports;
        }

        Type statementType = getVarType(jmmNode.getJmmChild(0)), functionType = symbolTable.getReturnType(jmmNode.getJmmParent().get("methodName"));
        Type functionTypeSanitized = new Type(functionType.getName().equals("int")? "integer" : functionType.getName(), functionType.isArray());
        boolean returnCorrect = equalTypes(statementType, functionTypeSanitized, symbolTable);
        if(!returnCorrect){
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Method "+jmmNode.getJmmParent().get("methodName")+" returns type "+functionTypeSanitized.getName()+" ."));
            putType(jmmNode, new Type("undefined", false));
        }
        else{
            putType(jmmNode, functionTypeSanitized);
        }

        
        return reports;
    }

    private List<Report> dealWithIfStatement(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();

        if(!jmmNode.getJmmChild(0).getJmmChild(0).hasAttribute("varType") || !jmmNode.getJmmChild(0).getJmmChild(0).hasAttribute("isArray")){
            //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Condition type couldn't be found."));
            return reports;
        }

        String type = jmmNode.getJmmChild(0).getJmmChild(0).get("varType");
        boolean isArray = jmmNode.getJmmChild(0).getJmmChild(0).get("isArray").equals("true");
        if(!equalTypes(new Type(type, isArray), new Type("boolean", false),symbolTable)){
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "If statement condition must be of type boolean."));
        }

        
        return reports;
    }

    private List<Report> dealWithStmt(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();
        JmmNode child = jmmNode.getJmmChild(0);

        // If 'this' object is used and function is static
        if(child.getKind().equals("Object") && jmmNode.getAncestor("MethodDeclaration").isPresent() && jmmNode.getAncestor("MethodDeclaration").get().hasAttribute("isStatic") && jmmNode.getAncestor("MethodDeclaration").get().get("isStatic").equals("static")){
            putType(jmmNode, new Type("undefined", false));
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Method "+jmmNode.getAncestor("MethodDeclaration").get().get("methodName")+" is static, can't use 'this' keyword."));
        }

        else{
            putType(jmmNode, getVarType(jmmNode.getJmmChild(0)));
        }

        
        return reports;
    }

    private List<Report> dealWithObject(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();
        putType(jmmNode, new Type(symbolTable.getClassName(), false));
        
        return reports;
    }

    private List<Report> dealWithArray(JmmNode jmmNode, SymbolTable symbolTable) {
        List<Report> reports = new ArrayList<>();
        List<Symbol> accessibleVars = getAccessibleVariables(getCallerFunctionName(jmmNode),symbolTable);

        if(!jmmNode.hasAttribute("variable")){
            putType(jmmNode, new Type("undefined", false));
            //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable couldn't be found."));
            return reports;
        }

        Type tp = matchVariable(accessibleVars, jmmNode.get("variable")), intArray = new Type("integer", true);

        if(!equalTypes(tp, intArray, symbolTable)){
            putType(jmmNode, new Type("undefined", false));
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+jmmNode.get("variable")+" not of type integer array."));
        }
        else{
            putType(jmmNode, intArray);
        }

        
        return reports;
    }

    private List<Report> dealWithLength(JmmNode jmmNode, SymbolTable symbolTable) {

        List<Report> reports = new ArrayList<>();

        if(!jmmNode.getJmmChild(0).hasAttribute("varType") || !jmmNode.getJmmChild(0).hasAttribute("isArray")){
            //reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable type couldn't be found."));
            putType(jmmNode, new Type("undefined", false));
            return reports;
        }

        Type tp = new Type(jmmNode.getJmmChild(0).get("varType"), jmmNode.getJmmChild(0).get("isArray").equals("true"));

        if(tp.getName().equals("boolean") || (tp.getName().equals("integer") && !tp.isArray())){
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable not of type array."));
        }

        putType(jmmNode, new Type("integer", false));
        return reports;
    }
}
