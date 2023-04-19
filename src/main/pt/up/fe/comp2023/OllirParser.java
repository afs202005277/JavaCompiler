package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OllirParser implements JmmOptimization {

    private void write_import(ArrayList<Symbol> imports) {
        if (imports != null)
            for (Symbol i : imports) {
                if (Objects.equals(i.getType().getName(), "library")) {
                    res.append("import ").append(i.getName()).append(";\n");
                }
            }
    }

    private void write_class(Symbol single_class, List<String> class_methods) {
        if (Objects.equals(single_class.getType().getName(), "class")) {
            this.res.append(single_class.getName()).append(Objects.equals(this.symbol_table.getSuper(), "") ? "" : " extends " + this.symbol_table.getSuper()).append(" {\n\n");
            write_fields();
            this.res.append("\n");
            this.write_methods(class_methods, single_class.getName());

            if (!this.has_constructor) {
                res.append(".construct ").append(single_class.getName()).append("(").append(this.write_parameters(this.symbol_table.getParameters(single_class.getName()))).append(").V {\n\tinvokespecial(this, \"<init>\").V;\n}\n");
            }

            this.res.append("\n}\n");
        }
    }

    private void write_method(String class_method_name, String class_name, List<Symbol> fields_method) {
        String[] tmp = class_method_name.split(" ");
        String method_name = tmp[tmp.length-1];
        if (Objects.equals(method_name, class_name)) {
            res.append(".construct ").append(method_name).append("(").append(this.write_parameters(fields_method)).append(").V {\n\tinvokespecial(this, \"<init>\").V;\n");
            this.method_insides_new(class_method_name);
            res.append("}\n\n");
            this.has_constructor = true;
        }
        res.append(".method ").append(class_method_name).append("(").append(this.write_parameters(fields_method)).append(").").append(this.convert_type(this.symbol_table.getReturnType(class_method_name))).append(" {\n");
        this.method_insides_new(class_method_name);
        res.append("\n}\n");
    }

    private JmmNode get_method_from_ast(String method_name) {
        List<JmmNode> methods = this.root_node.getJmmChild(this.root_node.getNumChildren()-1).getChildren();
        for (JmmNode m : methods) {
            if (Objects.equals(m.getKind(), "MethodDeclaration")) {
                if (Objects.equals(m.get("methodName"), method_name)) {
                    return m;
                }
            }
        }
        return null;
    }

    private boolean exists_in_variable(List<Symbol> local_variables, String var_name) {
        for (Symbol lv : local_variables) {
            if (Objects.equals(lv.getName(), var_name)) {
                return true;
            }
        }
        return false;
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

    private void write_methods(List<String> class_methods, String class_name) {
        for (String m : class_methods) {
            write_method(m, class_name, this.symbol_table.getParameters(m));
        }
    }

    private void write_fields()
    {
        JmmNode class_node = this.root_node.getJmmChild(1);

        for (JmmNode f : class_node.getChildren()) {
            if (Objects.equals(f.getKind(), "VarDeclaration")) {
                if (f.getNumChildren() == 1)
                    res.append(".field private ").append(f.get("variableName")).append(".").append(convert_type(new Type(f.getJmmChild(0).get("varType"), Objects.equals(f.get("isArray"), "true")))).append(";\n");
                else {
                    res.append(".field private ").append(f.getJmmChild(1).get("variable")).append(".").append(convert_type(new Type(f.getJmmChild(0).get("varType"), Objects.equals(f.get("isArray"), "true")))).append(" :=.").append(convert_type(new Type(f.getJmmChild(0).get("varType"), Objects.equals(f.get("isArray"), "true")))).append(" ").append(get_value_from_terminal_literal(f.getJmmChild(1).getJmmChild(0))).append(";\n");
                }
            }
        }
    }
    @Override
    public OllirResult toOllir(JmmSemanticsResult jmmSemanticsResult) {
        this.symbol_table = (SymbolTable) jmmSemanticsResult.getSymbolTable();
        this.root_node = jmmSemanticsResult.getRootNode();
        write_import(this.symbol_table.getSomethingFromTable("import"));
        res.append("\n");
        write_class(this.symbol_table.getSomethingFromTable("class").get(0), this.symbol_table.getMethods());

        System.out.println(res.toString());
        return new OllirResult(this.res.toString(), jmmSemanticsResult.getConfig());
    }

    SymbolTable symbol_table;
    JmmNode root_node;

    int temp_n;
    boolean has_constructor;

    StringBuilder res;

    public OllirParser() {
        this.symbol_table = null;
        this.root_node = null;
        this.temp_n = 0;
        this.res = new StringBuilder();
        this.has_constructor = false;
    }

    private void method_insides_new(String class_method) {

        //this.symbol_table.get
        String[] tmp = class_method.split(" ");
        String method_name = tmp[tmp.length-1];

        JmmNode method_node = this.get_method_from_ast(method_name);
        List<Symbol> local_variables = this.symbol_table.getLocalVariables(method_name);
        List<Symbol> parameter_variables = this.symbol_table.getParameters(method_name);
        List<Symbol> classfield_variables = this.symbol_table.getFields();

        assert method_node != null;
        for (JmmNode statement : method_node.getChildren()) {
            if (!Objects.equals(statement.getKind(), "ReturnType") && !Objects.equals(statement.getKind(), "MethodArgument"))
                method_insides_handler(statement, local_variables, parameter_variables, classfield_variables);
        }
        res.append("\n");
        boolean has_ret = false;
        for (JmmNode statement : method_node.getChildren()) {
            if (!Objects.equals(statement.getKind(), "ReturnType") && !Objects.equals(statement.getKind(), "MethodArgument") && statement.hasAttribute("ollirhelper")) {
                res.append(statement.get("beforehand")).append("\n");
                res.append(statement.get("ollirhelper")).append("\n");
            }
            if (Objects.equals(statement.getKind(), "ReturnStmt"))
                has_ret = true;
        }
        if (!has_ret)
            res.append("ret.V;\n");
    }

    private void method_insides_handler(JmmNode node, List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables) {
        node.put("beforehand", "");
        if (node.getNumChildren() != 0) {
            for (JmmNode statement : node.getChildren()) {
                method_insides_handler(statement, local_variables, parameter_variables, classfield_variables);
            }
            switch(node.getKind()) {
                case "ReturnStmt":
                    node.put("ollirhelper", handle_return_statement(node));
                    break;
                case "VarDeclaration":
                    node.put("ollirhelper", handle_variable_declaration(node, local_variables, parameter_variables, classfield_variables));
                    break;
                case "BinaryOp":
                    node.put("ollirhelper", handle_binary_ops(node));
                    break;
                case "Condition":
                    node.put("ollirhelper", node.getJmmChild(0).get("ollirhelper"));
                    break;
                case "Body", "ElseStmtBody":
                    node.put("ollirhelper", handle_bodies(node));
                    break;
                case "Assignment":
                    node.put("ollirhelper", handle_assignments(node, local_variables, parameter_variables, classfield_variables));
                    break;
                case "MethodCall":
                    node.put("ollirhelper", handle_method_calls(node, local_variables, parameter_variables, classfield_variables));
                    break;
                case "Parenthesis":
                    node.put("ollirhelper", handle_parenthesis(node));
                    break;
                case "IfStatement":
                    node.put("ollirhelper", handle_ifs(node));
                    break;
                case "Stmt":
                    handle_before_hand(node, new StringBuilder());
            }
        } else {
            switch (node.getKind()) {
                case "Type", "Object" -> handle_before_hand(node, new StringBuilder(""));
                case "Literal", "LiteralS" ->
                        node.put("ollirhelper", handle_literals(node, local_variables, parameter_variables, classfield_variables));
                case "ObjectInstantiation" -> {
                    node.put("ollirhelper", handle_object_instantiation(node));
                    node.put("id", node.get("ollirhelper"));
                }
            }
        }
    }

    private String handle_return_statement(JmmNode node) {
        StringBuilder res = new StringBuilder();
        JmmNode argument = node.getJmmChild(0);
        if (Objects.equals(argument.getKind(), "Literal")) {
            res.append("ret").append(".").append(get_var_type_from_name(node.getJmmChild(0).get("ollirhelper"))).append(" ").append(node.getJmmChild(0).get("ollirhelper")).append(";\n");
        } else {
            res.append("temp_").append(this.temp_n).append(".").append(get_var_type_from_name(argument.get("ollirhelper"))).append(" :=.").append(get_var_type_from_name(argument.get("ollirhelper"))).append(" ").append(argument.get("ollirhelper")).append(";\n");
            this.temp_n++;
            res.append("ret").append(".").append(get_var_type_from_name(node.getJmmChild(0).get("ollirhelper"))).append(" ").append("temp_").append(this.temp_n-1).append(".").append(get_var_type_from_name(argument.get("ollirhelper"))).append(";\n");
        }
        handle_before_hand(node, new StringBuilder(""));
        return res.toString();
    }

    private String handle_bodies(JmmNode node) {
        StringBuilder res = new StringBuilder();
        if (Objects.equals(node.getJmmChild(0).getKind(), "Body") || Objects.equals(node.getJmmChild(0).getKind(), "ElseStmtBody")) {
            res.append(node.getJmmChild(0).get("ollirhelper")).append("\n");
        } else {
            for (JmmNode c : node.getChildren()) {
                res.append(c.get("ollirhelper")).append(";\n");
            }
        }
        handle_before_hand(node, new StringBuilder(""));
        return res.toString();
    }

    private String handle_ifs(JmmNode node) {
        StringBuilder res = new StringBuilder();
        res.append("if (").append(node.getJmmChild(0).get("ollirhelper")).append(") goto ");
        if (node.getNumChildren() == 3) {
            res.append("else;\n");
        } else {
            res.append("endif;\n");
        }
        res.append(node.getJmmChild(1).get("ollirhelper"));
        if (node.getNumChildren() == 3) {
            res.append("else:\n");
            res.append(node.getJmmChild(2).get("ollirhelper"));
        }
        res.append("endif:\n");
        handle_before_hand(node, new StringBuilder(""));
        return res.toString();
    }

    private String handle_parenthesis(JmmNode node) {
        StringBuilder res = new StringBuilder();
        res.append("temp_").append(this.temp_n).append(".").append(get_var_type_from_name(node.getJmmChild(0).get("ollirhelper"))).append(" :=.").append(get_var_type_from_name(node.getJmmChild(0).get("ollirhelper"))).append(" ").append(node.getJmmChild(0).get("ollirhelper")).append(";\n");
        this.temp_n++;
        handle_before_hand(node, res);
        return "temp_" + (this.temp_n-1) + "." + get_var_type_from_name(node.getJmmChild(0).get("ollirhelper"));
    }

    private String handle_method_calls(JmmNode node, List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables) {
        StringBuilder res = new StringBuilder();
        ArrayList<Integer> args_is_bin_ops = new ArrayList<>();
        if (node.getChildren().size() > 1) {
            List<JmmNode> arguments = node.getChildren();
            for (int j = 1; j < arguments.size(); j++) {
                if (Objects.equals(arguments.get(j).getKind(), "BinaryOp")) {
                    args_is_bin_ops.add(this.temp_n);
                    res.append("temp_").append(this.temp_n).append(".").append(get_var_type_from_name(arguments.get(j).get("ollirhelper"))).append(" :=.").append(get_var_type_from_name(arguments.get(j).get("ollirhelper"))).append(" ").append(arguments.get(j).get("ollirhelper")).append(";\n");
                    this.temp_n++;
                } else {
                    args_is_bin_ops.add(-1);
                }
            }
        }
        String tmp_var = node.getJmmParent().hasAttribute("variable") ? node.getJmmParent().get("variable") : "V";
        if (!Objects.equals(node.getJmmParent().getKind(), "Stmt")) {
            res.append("temp_").append(this.temp_n).append(".");
            this.temp_n++;
            if (Objects.equals(get_return_type_of_method(node.get("method")), "unknown")) {
                if (exists_in_variable(local_variables, tmp_var)) {
                    // its a local variable
                    tmp_var = get_local_variable(tmp_var, local_variables);
                } else if (exists_in_variable(parameter_variables, tmp_var)) {
                    tmp_var =  get_parameter_variable(tmp_var, parameter_variables);
                } else if (exists_in_variable(classfield_variables, tmp_var)) {
                    tmp_var = get_classfield_variable(node.getJmmParent(), tmp_var, classfield_variables);
                } else {
                    tmp_var = "V";
                }
                res.append(get_var_type_from_name(tmp_var));
                res.append(" :=.").append(get_var_type_from_name(tmp_var)).append(" ");
            } else {
                res.append(get_return_type_of_method(node.get("method")));
                res.append(" :=.").append(get_return_type_of_method(node.get("method"))).append(" ");
            }
        }

        boolean is_variable = false;
        String variable = "";
        if (exists_in_variable(local_variables, node.getJmmChild(0).get("id"))) {
            // its a local variable
            is_variable = true;
            variable = get_local_variable(node.getJmmChild(0).get("id"), local_variables);
        } else if (exists_in_variable(parameter_variables, node.getJmmChild(0).get("id"))) {
            is_variable = true;
            variable =  get_parameter_variable(node.getJmmChild(0).get("id"), parameter_variables);
        } else if (exists_in_variable(classfield_variables, node.getJmmChild(0).get("id"))) {
            is_variable = true;
            variable = get_classfield_variable(node.getJmmChild(0), node.getJmmChild(0).get("id"), classfield_variables);
        }
        res.append((!Objects.equals(node.getJmmChild(0).get("id"), "this") && !is_variable ? "invokestatic(" + node.getJmmChild(0).get("id") : "invokevirtual(" + node.getJmmChild(0).get("id") + "." + get_var_type_from_name(variable) )).append(",\"").append(node.get("method")).append("\"");
        if (node.getChildren().size() > 1) {
            List<JmmNode> arguments = node.getChildren();
            for (int j = 1; j < arguments.size(); j++) {
                res.append(",");
                res.append(args_is_bin_ops.get(j-1) != -1 ? "temp_" + args_is_bin_ops.get(j-1) + "." + get_var_type_from_name(arguments.get(j).get("ollirhelper")) : arguments.get(j).get("ollirhelper"));
            }
        }
        res.append(").").append(!Objects.equals(node.getJmmChild(0).get("id"), "this") && !is_variable ? "V" : (get_return_type_of_method(node.get("method")) == "unknown" ? get_var_type_from_name(tmp_var) : get_return_type_of_method(node.get("method")))).append(";\n");
        if (Objects.equals(node.getJmmParent().getKind(), "Stmt")) {
            handle_before_hand(node, new StringBuilder());
            node.getJmmParent().put("ollirhelper", res.toString());
        } else
            handle_before_hand(node, res);

        return !Objects.equals(node.getJmmChild(0).get("id"), "this") && !is_variable ? "" : "temp_" + (this.temp_n-1) + "." + (get_return_type_of_method(node.get("method")) == "unknown" ? get_var_type_from_name(tmp_var) : get_return_type_of_method(node.get("method")));
    }

    private void handle_before_hand(JmmNode node, StringBuilder append) {
        StringBuilder res = new StringBuilder();
        for (JmmNode c : node.getChildren())
            if (!Objects.equals(c.get("beforehand"), ""))
                res.append(c.get("beforehand")).append("\n");
        res.append(append);
        node.put("beforehand", res.toString());
    }

    private String get_return_type_of_method(String method_name) {
        String res = "unknown";
        try {
            res = this.convert_type(this.symbol_table.getReturnType(method_name));
        } catch (Exception ignored) {
        }
        return res;
    }

    private String handle_assignments(JmmNode node, List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables) {
        String variable = "";
        if (exists_in_variable(local_variables, node.get("variable"))) {
            // its a local variable
            variable = get_local_variable(node.get("variable"), local_variables);
        } else if (exists_in_variable(parameter_variables, node.get("variable"))) {
            variable =  get_parameter_variable(node.get("variable"), parameter_variables);
        } else {
            variable =  get_classfield_variable(node, node.get("variable"), classfield_variables);
            handle_before_hand(node, new StringBuilder());
            return "putfield(this, " + node.get("variable") + "." + get_var_type_from_name(variable) + ", " + node.getJmmChild(0).get("ollirhelper") + ").V;";
        }
        handle_before_hand(node, new StringBuilder());
        return variable + " :=." + get_var_type_from_name(variable) + " " + node.getJmmChild(0).get("ollirhelper") + ";";
    }

    private String handle_binary_ops(JmmNode condition) {

        StringBuilder res_beforehand = new StringBuilder();
        Type tmp = new Type(condition.get("varType"), false);
        res_beforehand.append("temp_").append(this.temp_n).append(".").append(convert_type(tmp)).append(" :=.").append(convert_type(tmp)).append(" ");
        this.temp_n++;
        String variable = convert_type(tmp);
        variable = switch (condition.get("op")) {
            case "<", ">", "<=", ">=", "==", "!=" -> ".bool";
            default -> variable;
        };
        res_beforehand.append(condition.getJmmChild(0).get("ollirhelper")).append(" ").append(condition.get("op")).append(".").append(variable).append(" ").append(condition.getJmmChild(1).get("ollirhelper")).append(";\n");
        handle_before_hand(condition, res_beforehand);
        return "temp_" + (this.temp_n-1) + "." + convert_type(tmp);
    }

    private String get_var_type_from_name(String variable) {
        String var_type = "";
        String[] splitted = variable.split("\\.");
        if (variable.contains("array")) {
            var_type += "array.";
        }
        var_type += splitted[splitted.length-1];
        return var_type;
    }

    private String handle_variable_declaration(JmmNode node, List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables) {
        if (node.getNumChildren() > 1) {
            List<JmmNode> stat_child = node.getChildren();
            String var_name = "";

            var_name = stat_child.get(1).get("variable");
            String var_type;
            if (exists_in_variable(local_variables, var_name))
                var_type = get_local_variable(var_name, local_variables);
            else if (exists_in_variable(local_variables, var_name))
                var_type = get_parameter_variable(var_name, parameter_variables);
            else
                var_type = get_classfield_variable(stat_child.get(1), var_name, classfield_variables);

            if (var_type.contains("array")) {
                StringBuilder res = new StringBuilder();
                // Is an array
                String c_string = stat_child.get(1).get("contents");
                String[] contents = c_string.split(", ");
                contents[0] = contents[0].substring(1);
                contents[contents.length-1] = contents[contents.length-1].substring(0, contents[contents.length-1].length()-1);

                res.append(var_name).append(var_type).append(" :=").append(var_type).append(" new(array, ").append(contents.length).append(".i32)").append(var_type).append(";\n");

                String type_var_no_array = var_type.split("\\.")[2];
                int i = 0;
                for (String c : contents) {
                    res.append("i.i32 :=.i32 ").append(i).append(".i32;\n");
                    res.append(var_name).append("[").append("i").append(".i32].").append(type_var_no_array).append(" :=.").append(type_var_no_array).append(" ").append(c).append(".").append(type_var_no_array).append(";\n");
                    i++;
                }
                handle_before_hand(node, new StringBuilder(""));
                return res.toString();
            } else {
                handle_before_hand(node, new StringBuilder());
                return node.getJmmChild(1).get("ollirhelper");
            }
        }
        handle_before_hand(node, new StringBuilder(""));
        return "";
    }

    private String handle_object_instantiation(JmmNode node) {
        StringBuilder res = new StringBuilder();
        res.append("temp_").append(this.temp_n).append(".").append(node.get("objectName")).append(" :=.").append(node.get("objectName")).append(" new(").append(node.get("objectName")).append(").").append(node.get("objectName")).append(";\n");
        this.temp_n++;
        res.append("invokespecial(").append("temp_").append(this.temp_n-1).append(".").append(node.get("objectName")).append(",\"<init>\").V;\n");
        handle_before_hand(node, res);
        return "temp_" + (this.temp_n-1) + "." + node.get("objectName");
    }

    private String handle_literals(JmmNode node, List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables) {
        if (node.hasAttribute("id")) {
            String variable_name = node.get("id");
            if (exists_in_variable(local_variables, variable_name)) {
                // its a local variable
                return get_local_variable(variable_name, local_variables);
            } else if (exists_in_variable(parameter_variables, variable_name)) {
                return get_parameter_variable(variable_name, parameter_variables);
            } else {
                return get_classfield_variable(node, variable_name, classfield_variables);
            }
        }
        handle_before_hand(node, new StringBuilder(""));
        return get_value_from_terminal_literal(node);
    }

    private String get_value_from_terminal_literal(JmmNode literal) {
        if (literal.hasAttribute("bool")) {
            return literal.get("bool") + ".bool";
        } else if (literal.hasAttribute("integer")) {
            return literal.get("integer") + ".i32";
        }
        return "unknown.unknown";
    }

    private String convert_type(Type t) {
        String tmp = "";
        if (t.isArray())
            tmp = "array.";
        String name = t.getName();
        if (name.contains("[]"))
            name = name.substring(0, name.length()-2);
        switch (name) {
            case "int", "integer" -> {
                return tmp + "i32";
            }
            case "bool", "boolean" -> {
                return tmp + "bool";
            }
            case "void" -> {
                return tmp + "V";
            }
            case "String" -> {
                return tmp + "String";
            }
        }
        return name;
    }

    private String get_local_variable(String var_name, List<Symbol> local_variables) {
        for (Symbol lv : local_variables) {
            if (Objects.equals(lv.getName(), var_name)) {
                return var_name + "." + convert_type(lv.getType());
            }
        }
        return "error.error";
    }

    private String get_parameter_variable(String var_name, List<Symbol> parameter_variables) {
        int i = 1;
        for (Symbol lv : parameter_variables) {
            if (Objects.equals(lv.getName(), var_name)) {
                return "$" + i + "." + var_name + "." + convert_type(lv.getType());
            }
            i++;
        }
        return "error.error";
    }

    private String get_classfield_variable(JmmNode node, String var_name, List<Symbol> classfield_variables) {
        StringBuilder res = new StringBuilder();
        for (Symbol lv : classfield_variables) {
            if (Objects.equals(lv.getName(), var_name)) {
                res.append("temp_").append(this.temp_n).append(".").append(convert_type(lv.getType())).append(" :=.").append(convert_type(lv.getType())).append(" getfield(this, ").append(var_name).append(".").append(convert_type(lv.getType())).append(").").append(convert_type(lv.getType())).append(";\n");
                this.temp_n++;
                handle_before_hand(node, res);
                return "temp_" + (this.temp_n-1) + "." + convert_type(lv.getType());
            }
        }
        handle_before_hand(node, res);
        return "error.error";
    }
}
