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
        addVisit("LiteralS", this::dealWithLiteralS);
        addVisit("ArrayIndex", this::dealWithArrayIndex);
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
        // verificar se children[1] é do Tipo Int
        return new ArrayList<>();
    }

    private List<Report> dealWithLiteralS(JmmNode jmmNode, SymbolTable symbolTable) {

        List<Report> reports = new ArrayList<>();

        String name = jmmNode.get("id");

        String functionName = this.getCallerFunctionName(jmmNode);

        List<Symbol> functionVars = getFunctionVariables(functionName, symbolTable);

        pt.up.fe.comp.jmm.analysis.table.Type varType = matchVariable(functionVars, name);

        if(varType == null && (!jmmNode.getJmmParent().getKind().equals("MethodCall"))){
            jmmNode.put("varType", "undefined");
            Report rep = new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")), "Variable "+ name+" can't be found.\n");
            reports.add(rep);
        }
        else if(varType != null && (!jmmNode.getJmmParent().getKind().equals("MethodCall"))){
            jmmNode.put("varType", varType.getName());
            jmmNode.put("isArray", String.valueOf(varType.isArray()));
        }

        else{
            jmmNode.put("varType", "libraryMethodInvocation");
            jmmNode.put("isArray", "false");
        }

        return reports;

    }



}
