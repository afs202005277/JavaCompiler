package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;

import java.util.ArrayList;
import java.util.List;

public class Analyser implements JmmAnalysis {
    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult jmmParserResult) {

        SymbolTable symbolTable = new SymbolTable();
        JmmSemanticsResult jmmSemanticsResult = symbolTable.semanticAnalysis(jmmParserResult);
        System.out.println("\n==========================\n");
        List<Report> reports = new SemanticAnalysis().visit(jmmSemanticsResult.getRootNode(), (SymbolTable) jmmSemanticsResult.getSymbolTable());
        System.out.println("\n==========================\n");
        System.out.println(reports);

        return new JmmSemanticsResult(jmmParserResult, jmmSemanticsResult.getSymbolTable(), reports);
    }
}