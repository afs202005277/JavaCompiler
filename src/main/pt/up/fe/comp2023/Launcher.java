package pt.up.fe.comp2023;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsLogs;
import pt.up.fe.specs.util.SpecsSystem;
import pt.up.fe.comp2023.JasminConverter;

public class Launcher {

    public static void main(String[] args) throws FileNotFoundException {
        // Setups console logging and other things
        SpecsSystem.programStandardInit();

        // Parse arguments as a map with predefined options
        var config = parseArgs(args);

        // Get input file
        File inputFile = new File(config.get("inputFile"));

        // Check if file exists
        if (!inputFile.isFile()) {
            throw new RuntimeException("Expected a path to an existing input file, got '" + inputFile + "'.");
        }

        // Read contents of input file
        String code = SpecsIo.read(inputFile);

        String content = new Scanner(new File("./test/pt/up/fe/comp/cp2/apps/example_ollir/HelloWorld.ollir")).useDelimiter("\\Z").next();
        // System.out.println(content);
        JasminConverter jasminConverter = new JasminConverter();
        System.out.println(jasminConverter.toJasmin(new OllirResult(content, config)).getJasminCode());


        // Instantiate JmmParser
        /*SimpleParser parser = new SimpleParser();

        // Parse stage
        JmmParserResult parserResult = parser.parse(code, config);
        if (parserResult == null){
            System.out.println(new Report(ReportType.ERROR, Stage.SYNTATIC,-1, -1, "[PARSING ERROR] Invalid characters detected, terminating."));
        }
        else if (parserResult.getRootNode() != null) {
            System.out.println(parserResult.getRootNode().toTree());

            // ... add remaining stages
            SymbolTable symbolTable = new SymbolTable();
            JmmSemanticsResult jmmSemanticsResult = symbolTable.semanticAnalysis(parserResult);
            jmmSemanticsResult.getSymbolTable().print();

        } else {
            for (pt.up.fe.comp.jmm.report.Report temp : parserResult.getReports()) {
                System.out.println(temp);
                System.out.println('\n');
            }
        }*/
    }

    private static Map<String, String> parseArgs(String[] args) {
        SpecsLogs.info("Executing with args: " + Arrays.toString(args));

        // Check if there is at least one argument
        if (args.length != 1) {
            throw new RuntimeException("Expected a single argument, a path to an existing input file.");
        }

        // Create config
        Map<String, String> config = new HashMap<>();
        config.put("inputFile", args[0]);
        config.put("optimize", "false");
        config.put("registerAllocation", "-1");
        config.put("debug", "false");

        return config;
    }

}
