package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ASTConverter extends AJmmVisitor<String, String> {
    private final SymbolTable symbolTable;

    public SymbolTable getTable() {
        return this.symbolTable;
    }

    public ASTConverter() {
        this.symbolTable = new SymbolTable();
    }

    protected void buildVisitor() {
        this.setDefaultVisit(this::visitDefault);
        addVisit("Program", this::dealWithProgram);
        addVisit("ImportDeclaration", this::dealWithImport);
        addVisit("ClassDeclaration", this::dealWithClass);
        addVisit("VarDeclaration", this::dealWithFields);
        addVisit("MethodArgument", this::dealWithMethodArguments);
        addVisit("Assignment", this::dealWithAssignment);
        addVisit("Integer", this::dealWithLiteral);
        addVisit("Identifier", this::dealWithLiteral);
        addVisit("ExprStmt", this::dealWithExprStmt);
        addVisit("BinaryOp", this::dealWithBinaryOp);
        addVisit("MethodDeclaration", this::dealWithMethods);
    }

    private String visitDefault(JmmNode jmmNode, String s) {
        List<JmmNode> children = jmmNode.getChildren();
        for (JmmNode child : children) {
            visit(child);
        }
        return "";
    }

    private String dealWithMethodArguments(JmmNode jmmNode, String s) {
        String argumentType = jmmNode.getJmmChild(0).getJmmChild(0).get("varType");
        boolean isArray = argumentType.contains("[]");
        if (isArray) {
            argumentType = argumentType.substring(0, argumentType.indexOf("[]"));
        }
        Type type = new Type(argumentType, isArray);
        Symbol symbol = new Symbol(type, jmmNode.get("argumentName"));
        ArrayList<Symbol> list = new ArrayList<>();
        list.add(symbol);
        this.symbolTable.addEntry(jmmNode.getJmmParent().get("methodName") + "_params", list);
        return "";
    }

    private String dealWithMethods(JmmNode jmmNode, String s) {
        // TODO: experimentar com main errado
        if (Objects.equals(jmmNode.get("methodName"), "main")) {
            Type type = new Type("void", false);
            Symbol symbol = new Symbol(type, "main");
            ArrayList<Symbol> list = new ArrayList<>();
            list.add(symbol);
            this.symbolTable.addEntry("methods", list);

            type = new Type("String", true);
            symbol = new Symbol(type, jmmNode.get("argumentName"));
            list = new ArrayList<>();
            list.add(symbol);
            this.symbolTable.addEntry("main_params", list);
        } else {
            String argumentType = jmmNode.getJmmChild(0).getJmmChild(0).get("varType");
            boolean isArray = argumentType.contains("[]");
            if (isArray) {
                argumentType = argumentType.substring(0, argumentType.indexOf("[]"));
            }
            Type type = new Type(argumentType, isArray);
            Symbol symbol = new Symbol(type, jmmNode.get("methodName"));
            ArrayList<Symbol> list = new ArrayList<>();
            list.add(symbol);
            this.symbolTable.addEntry("methods", list);
        }
        for (JmmNode child : jmmNode.getChildren()) {
            visit(child);
        }
        return "";
    }

    private String dealWithFields(JmmNode jmmNode, String s) {
        if (Objects.equals(jmmNode.getJmmParent().getKind(), "MethodDeclaration")) {
            String argumentType = jmmNode.getJmmChild(0).get("varType");
            boolean isArray = argumentType.contains("[]");
            if (isArray) {
                argumentType = argumentType.substring(0, argumentType.indexOf("[]"));
            }
            Type type = new Type(argumentType, isArray);
            Symbol symbol;
            if (jmmNode.getNumChildren() > 1) {
                symbol = new Symbol(type, jmmNode.getJmmChild(1).get("variable"));
            } else {
                symbol = new Symbol(type, jmmNode.get("variableName"));
            }
            ArrayList<Symbol> list = new ArrayList<>();
            list.add(symbol);
            this.symbolTable.addEntry(jmmNode.getJmmParent().get("methodName") + "_variables", list);
        } else {
            String argumentType = jmmNode.getJmmChild(0).get("varType");
            boolean isArray = argumentType.contains("[]");
            if (isArray) {
                argumentType = argumentType.substring(0, argumentType.indexOf("[]"));
            }
            Type type = new Type(argumentType, isArray);
            Symbol symbol;
            if (jmmNode.getNumChildren() > 1) {
                symbol = new Symbol(type, jmmNode.getJmmChild(1).get("variable"));
            } else {
                symbol = new Symbol(type, jmmNode.get("variableName"));
            }
            ArrayList<Symbol> list = new ArrayList<>();
            list.add(symbol);
            this.symbolTable.addEntry("fields", list);
        }
        return "";
    }

    private String dealWithClass(JmmNode jmmNode, String s) {
        Type type = new Type("class", false);
        Symbol symbol = new Symbol(type, jmmNode.get("className"));
        ArrayList<Symbol> list = new ArrayList<>();
        list.add(symbol);

        this.symbolTable.addEntry("class", list);

        if (jmmNode.hasAttribute("extendedClassName")) {
            type = new Type("extends", false);
            symbol = new Symbol(type, jmmNode.get("extendedClassName"));
            list = new ArrayList<>();
            list.add(symbol);
            this.symbolTable.addEntry("extends", list);
        }

        for (JmmNode child : jmmNode.getChildren()) {
            visit(child);
        }
        return "";
    }

    private String dealWithImport(JmmNode jmmNode, String s) {
        Type type = new Type("library", false);
        StringBuilder importName = new StringBuilder();
        for (JmmNode child : jmmNode.getChildren()){
            importName.append(child.get("subImportName"));
            importName.append(".");
        }
        importName.deleteCharAt(importName.length()-1);
        Symbol symbol = new Symbol(type, importName.toString());
        ArrayList<Symbol> list = new ArrayList<>();
        list.add(symbol);

        this.symbolTable.addEntry("import", list);
        return "";
    }

    private String dealWithBinaryOp(JmmNode jmmNode, String s) {
        return "";
    }

    private String dealWithExprStmt(JmmNode jmmNode, String s) {
        return "";
    }

    private String dealWithProgram(JmmNode jmmNode, String s) {
        for (JmmNode child : jmmNode.getChildren()) {
            visit(child);
        }
        return "";
    }

    private String dealWithAssignment(JmmNode jmmNode, String s) {
        return "";
    }

    private String dealWithLiteral(JmmNode jmmNode, String s) {
        return "";
    }
}