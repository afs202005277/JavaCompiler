package pt.up.fe.comp2023;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import pt.up.fe.comp.jmm.ast.antlr.AntlrParser;
import pt.up.fe.comp.jmm.parser.JmmParser;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2023.JavammLexer;
import pt.up.fe.comp2023.JavammParser;
import pt.up.fe.comp.jmm.jasmin.JasminBackend;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

/**
 * Copyright 2022 SPeCS.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">...</a>
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License. under the License.
 */

public class SimpleParser implements JmmParser {

    @Override
    public String getDefaultRule() {
        return "program";
    }

    @Override
    public JmmParserResult parse(String jmmCode, String startingRule, Map<String, String> config) {

        // Convert code string into a character stream
        var input = new ANTLRInputStream(jmmCode);
        // Transform characters into tokens using the lexer
        var lex = new JavammLexer(input);
        // Wrap lexer around a token stream
        var tokens = new CommonTokenStream(lex);
        // Transforms tokens into a parse tree
        var parser = new JavammParser(tokens);

        try {
            // Convert ANTLR CST to JmmNode AST
            return AntlrParser.parse(lex, parser, startingRule)
                    // If there were no errors and a root node was generated, create a JmmParserResult with the node
                    .map(root2 -> new JmmParserResult(root2, Collections.emptyList(), config))
                    // If there were errors, create an error JmmParserResult without root node
                    .orElseGet(() -> {
                        if (parser.getNumberOfSyntaxErrors() > 0) {
                            System.out.println(new Report(ReportType.ERROR, Stage.SYNTATIC, -1, -1, "[PARSING ERROR] " + parser.getNumberOfSyntaxErrors() + (parser.getNumberOfSyntaxErrors() == 1 ? " error " : " errors ") + "while parsing"));
                        }
                        return JmmParserResult.newError(new Report(ReportType.ERROR, Stage.SYNTATIC, -1, "There were syntax errors during parsing, terminating"));
                    });

        } catch (Exception e) {
            if (parser.getNumberOfSyntaxErrors() > 0) {
                System.out.println(new Report(ReportType.ERROR, Stage.SYNTATIC, -1, -1, "[PARSING ERROR] " + e.getMessage()));
                return JmmParserResult.newError(new Report(ReportType.ERROR, Stage.SYNTATIC, -1, -1, "[PARSING ERROR] " + parser.getNumberOfSyntaxErrors() + (parser.getNumberOfSyntaxErrors() == 1 ? " error " : " errors ") + "while parsing"));
            }
        }

        return null;
    }
}
