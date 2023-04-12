package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.lang.reflect.Array;
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
            return single_class.getName() + (Objects.equals(this.symbol_table.getSuper(), "") ? "" : " extends " + this.symbol_table.getSuper()) + " {\n\n" + this.write_fields() + "\n" + this.write_methods(class_methods, single_class.getName()) + "\n}\n";
        }
        return "";
    }

    private String write_method(String class_method_name, String class_name, List<Symbol> fields_method) {
        String[] tmp = class_method_name.split(" ");
        String method_name = tmp[tmp.length-1];
        if (Objects.equals(method_name, class_name)) {
            return ".construct " + method_name + "(" + this.write_parameters(fields_method) + ").V {\n\tinvokespecial(this, \"<init>\").V;\n}\n\n";
        }
        return ".method " + class_method_name + "(" + this.write_parameters(fields_method) + ")." + this.convert_type(this.symbol_table.getReturnType(class_method_name)) + " {\n" + this.method_insides(class_method_name) + "\n}\n\n";
    }

    private JmmNode get_method_from_ast(String method_name) {
        List<JmmNode> methods = this.root_node.getJmmChild(1).getChildren();
        for (JmmNode m : methods) {
            if (Objects.equals(m.getKind(), "MethodDeclaration")) {
                if (Objects.equals(m.get("methodName"), method_name)) {
                    return m;
                }
            }
        }
        return null;
    }

    private String method_insides(String class_method) {

        //this.symbol_table.get
        String[] tmp = class_method.split(" ");
        String method_name = tmp[tmp.length-1];

        JmmNode method_node = this.get_method_from_ast(method_name);
        List<Symbol> local_variables = this.symbol_table.getLocalVariables(method_name);
        StringBuilder res = new StringBuilder();

        assert method_node != null;
        for (JmmNode statement : method_node.getChildren()) {
            switch (statement.getKind()) {
                case "VarDeclaration" -> {
                    List<JmmNode> stat_child = statement.getChildren();
                    String var_name = stat_child.get(1).get("variable");
                    StringBuilder type_var = new StringBuilder(".");
                    for (Symbol lv : local_variables) {
                        if (Objects.equals(lv.getName(), var_name)) {
                            type_var.append(convert_type(lv.getType()));
                            break;
                        }
                    }
                    if (stat_child.size() > 1) {
                        // Has Assignment
                        if (type_var.toString().contains("array")) {
                            // Is an array
                            String c_string = stat_child.get(1).get("contents");
                            String[] contents = c_string.split(", ");
                            contents[0] = contents[0].substring(1);
                            contents[contents.length-1] = contents[contents.length-1].substring(0, contents[contents.length-1].length()-1);

                            res.append(var_name).append(type_var).append(" :=").append(type_var).append(" new(array, ").append(contents.length).append(".i32)").append(type_var).append(";\n");

                            String type_var_no_array = type_var.toString().split("\\.")[2];
                            int i = 0;
                            for (String c : contents) {
                                res.append(var_name).append("[").append(i).append(".i32].").append(type_var_no_array).append(" :=.").append(type_var_no_array).append(" ").append(c).append(".").append(type_var_no_array).append(";\n");
                                i++;
                            }
                        } else {
                            // NEEDS TO BE DONE
                        }
                    }
                }
                case "IfStatement" -> {

                }
                case "ReturnStmt" -> {

                }
            }
        }

        return res.toString();
    }

    private String write_parameters(List<Symbol> fields_method) {
        StringBuilder res = new StringBuilder();
        for (Symbol f : fields_method) {
            res.append(f.getName()).append(".").append(this.convert_type(f.getType())).append(", ");
        }
        if (res.length() > 2) {
            res.delete(res.length() - 2, res.length());
        }
        return res.toString();
    }

    private String convert_type(Type t) {
        String tmp = "";
        if (t.isArray())
            tmp = "array.";
        String name = t.getName();
        switch (name) {
            case "int" -> {
                return tmp + "i32";
            }
            case "bool" -> {
                return tmp + "bool";
            }
            case "void" -> {
                return tmp + "V";
            }
            case "String" -> {
                return tmp + "String";
            }
        }
        return "unknown";
    }

    private String write_methods(List<String> class_methods, String class_name) {
        StringBuilder res = new StringBuilder();
        for (String m : class_methods) {
            res.append(write_method(m, class_name, this.symbol_table.getParameters(m)));
        }
        return res.toString();
    }

    private String write_fields()
    {
        List<Symbol> fields = this.symbol_table.getFields();
        StringBuilder res = new StringBuilder();
        for (Symbol f : fields) {
            res.append(".field private ").append(f.getName()).append(".").append(this.convert_type(f.getType())).append(";\n");
        }
        return res.toString();
    }
    @Override
    public OllirResult toOllir(JmmSemanticsResult jmmSemanticsResult) {
        this.symbol_table = (SymbolTable) jmmSemanticsResult.getSymbolTable();
        this.root_node = jmmSemanticsResult.getRootNode();
        String ollirCode = write_import(this.symbol_table.getSomethingFromTable("import")) +
                '\n' +
                this.write_class(this.symbol_table.getSomethingFromTable("class").get(0), this.symbol_table.getMethods());

        return new OllirResult(ollirCode, jmmSemanticsResult.getConfig());
    }

    SymbolTable symbol_table;
    JmmNode root_node;

    OllirParser() {
        this.symbol_table = null;
        this.root_node = null;
    }
}
