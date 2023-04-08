package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;

import java.util.ArrayList;
import java.util.List;

public class SemanticAnalysis extends PostorderJmmVisitor<SymbolTable, List<Report>> {

    @Override
    protected void buildVisitor() {
        /*addVisit("ArrayAccess", this::dealWithArrayAccess);
        addVisit("MethodCall", this::dealWithMethodCall);
        addVisit("Assignment", this::dealWithAssignment);
        addVisit("Type", this::dealWithType);
        addVisit("BinaryOp", this::dealWithBinaryOp);
        addVisit("Identifier", this::dealWithIdentifier);
        addVisit("Integer", this::dealWithInteger);
        addVisit("Bool", this::dealWithBoolean);
        addVisit("Scope", this::dealWithScope);
        addVisit("MethodCall", this::dealWithMethodCall);
        addVisit("Unary", this::dealWithUnary);*/
        addVisit("ArrayIndex", this::dealWithArrayIndex);
        this.setDefaultVisit(this::visitDefault);
    }

    private List<Report> dealWithArrayIndex(JmmNode jmmNode, SymbolTable symbolTable) {
        jmmNode.getChildren();
        return new ArrayList<>();

        // 1º verificar se children[0] (onde esta o nome do array) existe
        // 2º verificar se children[1] é do Tipo LiteralArrayAccess
    }


    private List<Report> visitDefault(JmmNode jmmNode, SymbolTable symbolTable) {
        System.out.println(jmmNode.getKind());
        return new ArrayList<>();
    }



}
