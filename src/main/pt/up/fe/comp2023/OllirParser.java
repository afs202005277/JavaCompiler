package pt.up.fe.comp2023;

import org.specs.comp.ollir.ClassUnit;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OllirParser implements JmmOptimization {

    private String write_import(ArrayList<Symbol> imports) {
        StringBuilder res = new StringBuilder();
        for (Symbol i : imports) {
            if (Objects.equals(i.getType().getName(), "library")) {
                res.append("import ").append(i.getName()).append(";\n");
            }
        }
        return res.toString();
    }

    private String write_class(Symbol single_class, List<String> class_methods) {
        if (Objects.equals(single_class.getType().getName(), "class")) {
            return single_class.getName() + " {\n\n" + this.write_methods(class_methods, single_class.getName()) + "\n}\n";
        }
        return "";
    }

    private String write_varformat(Symbol var) {
        String type = "";

        return var.getName() + "." + type;
    }

    private String write_method(String class_method_name, String class_name, List<Symbol> fields_method) {
        if (Objects.equals(class_method_name, class_name)) {
            return ".construct " + class_method_name + "(" + this.write_fields(fields_method) + ")." + this.convert_type(this.symbol_table.getReturnType(class_method_name).getName()) + " {\n" + this.method_insides(class_method_name, class_name) + "\n}\n\n";
        }
        return ".method " + class_method_name + "(" + this.write_fields(fields_method) + ")." + this.convert_type(this.symbol_table.getReturnType(class_method_name).getName()) + " {\n" + this.method_insides(class_method_name, class_name) + "\n}\n\n";
    }

    private String method_insides(String class_method, String class_name) {
        return "";
    }

    private String write_fields(List<Symbol> fields_method) {
        StringBuilder res = new StringBuilder();
        for (Symbol f : fields_method) {
            res.append(f.getName()).append(".").append(this.convert_type(f.getType().getName())).append(", ");
        }
        if (res.length() > 2) {
            res.delete(res.length() - 3, 2);
        }
        return res.toString();
    }

    private String convert_type(String name) {
        switch (name) {
            case "int" -> {
                return "i32";
            }
            case "bool" -> {
                return "bool";
            }
        }
        return "unknown";
    }

    private String write_methods(List<String> class_methods, String class_name) {
        StringBuilder res = new StringBuilder();
        for (String m : class_methods) {
            res.append(write_method(m, class_name, this.symbol_table.getFields()));
        }
        return res.toString();
    }

    @Override
    public OllirResult toOllir(JmmSemanticsResult jmmSemanticsResult) {
        this.symbol_table = (SymbolTable) jmmSemanticsResult.getSymbolTable();
        StringBuilder ollirCode = new StringBuilder();
        ollirCode.append(write_import(this.symbol_table.getSomethingFromTable("import")));
        ollirCode.append(this.write_class(this.symbol_table.getSomethingFromTable("class").get(0), this.symbol_table.getMethods()));

        return new OllirResult(ollirCode.toString(), jmmSemanticsResult.getConfig());
    }

    SymbolTable symbol_table;
    OllirParser() {
        this.symbol_table = null;
    }
}
