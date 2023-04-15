package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import javax.swing.text.html.HTMLDocument;
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
            this.res.append("\n}\n");
        }
    }

    private void write_method(String class_method_name, String class_name, List<Symbol> fields_method) {
        String[] tmp = class_method_name.split(" ");
        String method_name = tmp[tmp.length-1];
        if (Objects.equals(method_name, class_name)) {
            res.append(".construct ").append(method_name).append("(").append(this.write_parameters(fields_method)).append(").V {\n\tinvokespecial(this, \"<init>\").V;\n}\n\n");
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

    private String method_insides(String class_method) {

        //this.symbol_table.get
        String[] tmp = class_method.split(" ");
        String method_name = tmp[tmp.length-1];

        JmmNode method_node = this.get_method_from_ast(method_name);
        List<Symbol> local_variables = this.symbol_table.getLocalVariables(method_name);
        List<Symbol> parameter_variables = this.symbol_table.getParameters(method_name);
        List<Symbol> classfield_variables = this.symbol_table.getFields();

        assert method_node != null;

        return write_method_insides_from_node(method_node, local_variables, parameter_variables, classfield_variables);
    }

    private String write_method_insides_from_node(JmmNode method_node, List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables) {
        assert method_node != null;
        for (JmmNode statement : method_node.getChildren()) {
            switch (statement.getKind()) {
                case "Stmt" -> {
                    res.append(binary_ops_handler(local_variables, parameter_variables, classfield_variables, statement.getJmmChild(0))).append(";\n");
                }
                case "Assignment" -> {
                    if (statement.getChildren().size() > 1) {
                        String var_type = get_only_type_variable(statement.get("id"), local_variables, parameter_variables, classfield_variables);
                        res.append(statement.get("id")).append("[").append(get_value_from_terminal_literal(statement.getJmmChild(0))).append("]").append(var_type).append(" :=").append(var_type).append(" ").append(binary_ops_handler(local_variables, parameter_variables, classfield_variables, statement.getJmmChild(1))).append(";\n");
                    } else {
                        if (exists_in_variable(classfield_variables, statement.get("variable"))) {
                            res.append(get_variable(statement.get("variable"), local_variables, parameter_variables, classfield_variables, get_value_from_terminal_literal(statement.getJmmChild(0)), false)).append(";\n");
                        } else {
                            String var = get_variable(statement.get("variable"), local_variables, parameter_variables, classfield_variables, get_value_from_terminal_literal(statement.getJmmChild(0)), false);
                            String var_type = get_only_type_variable(statement.get("variable"), local_variables, parameter_variables, classfield_variables);
                            res.append(var).append(" :=").append(var_type).append(" ");
                            if (Objects.equals(statement.getJmmChild(0).getKind(), "BinaryOp")) {
                                res.append(binary_ops_handler(local_variables, parameter_variables, classfield_variables, statement.getJmmChild(0))).append(";\n");
                            } else {
                                res.append(get_value_from_terminal_literal(statement.getJmmChild(0))).append(";\n");
                            }
                        }
                    }
                }
                case "VarDeclaration" -> {
                    List<JmmNode> stat_child = statement.getChildren();
                    String var_name = "";
                    if (stat_child.size() > 1) {
                        var_name = stat_child.get(1).get("variable");
                    } else {
                        var_name = statement.get("variableName");
                    }
                    String type_var = get_only_type_variable(var_name, local_variables, parameter_variables, classfield_variables);
                    if (stat_child.size() > 1) {
                        // Has Assignment
                        if (type_var.contains("array")) {
                            // Is an array
                            String c_string = stat_child.get(1).get("contents");
                            String[] contents = c_string.split(", ");
                            contents[0] = contents[0].substring(1);
                            contents[contents.length-1] = contents[contents.length-1].substring(0, contents[contents.length-1].length()-1);

                            res.append(var_name).append(type_var).append(" :=").append(type_var).append(" new(array, ").append(contents.length).append(".i32)").append(type_var).append(";\n");

                            String type_var_no_array = type_var.split("\\.")[2];
                            int i = 0;
                            for (String c : contents) {
                                res.append("i.i32 :=.i32 ").append(i).append(".i32;\n");
                                res.append(var_name).append("[").append("i").append(".i32].").append(type_var_no_array).append(" :=.").append(type_var_no_array).append(" ").append(c).append(".").append(type_var_no_array).append(";\n");
                                i++;
                            }
                        }
                    }
                }
                case "IfStatement" -> {
                    System.out.println("Hello!");
                    res.append("if (");
                    List<JmmNode> if_children = statement.getChildren();
                    JmmNode condition = if_children.get(0);
                    res.append(binary_ops_handler(local_variables, parameter_variables, classfield_variables, condition.getJmmChild(0)));
                    if (statement.getChildren().size() == 3)
                        res.append(") goto else;\n");
                    else
                        res.append(") goto end;\n");


                    JmmNode if_body = if_children.get(1);
                    if (!Objects.equals(if_children.get(1).getKind(), "Body")) {
                        if_body = new JmmNodeImpl("Body");
                        if_body.add(if_children.get(1));
                    }
                    res.append(write_method_insides_from_node(if_body, local_variables, parameter_variables, classfield_variables));
                    System.out.println("aa");
                    if (statement.getChildren().size() == 3) {
                        res.append("else:\n");
                        res.append(write_method_insides_from_node(statement.getJmmChild(2), local_variables, parameter_variables, classfield_variables));
                    } else {
                        res.append("end:\n");
                    }
                }
                case "ReturnStmt" -> {
                    String var = binary_ops_handler(local_variables, parameter_variables, classfield_variables, statement.getJmmChild(0));
                    String[] tmp = var.split("\\.");
                    String var_type = ".";
                    if (var.contains("array"))
                        var_type += tmp[tmp.length-2] + "." + tmp[tmp.length-1];
                    else
                        var_type += tmp[tmp.length-1];
                    res.append("ret").append(var_type).append(" ").append(var).append(";\n");
                }
            }
        }
        return res.toString();
    }

    private String get_only_type_variable(String id, List<Symbol> localVariables, List<Symbol> parameterVariables, List<Symbol> classFieldVariables) {
        if (exists_in_variable(localVariables, id)) {
            return get_variable_type(localVariables, id);
        } else if (exists_in_variable(parameterVariables, id)) {
            return get_variable_type(parameterVariables, id);
        } else {
            return get_variable_type(classFieldVariables, id);
        }
    }

    private String get_variable(String id, List<Symbol> localVariables, List<Symbol> parameterVariables, List<Symbol> classFieldVariables, String value_assignment, boolean get) {
        String var_type = "";
        if (exists_in_variable(localVariables, id)) {
            var_type = get_variable_type(localVariables, id);
            return id + var_type;
        } else if (exists_in_variable(parameterVariables, id)) {
            return get_parameter_variable(parameterVariables, id);
        } else {
            var_type = get_variable_type(classFieldVariables, id);
            if (get)
                return "getfield(this, " + id + var_type + ")" + var_type;
            else
                return "putfield(this, " + id + var_type + ", " + value_assignment + ").V";
        }
    }

    private String binary_ops_handler(List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables, JmmNode condition) {
        ArrayList<JmmNode> condition_builder = new ArrayList<>();
        condition_builder.add(condition);
        for (int i = 0; i < condition_builder.size(); i++) {
            if (Objects.equals(condition_builder.get(i).getKind(), "BinaryOp")) {
                if (!condition_builder.contains(condition_builder.get(i).getJmmChild(0))) {
                    condition_builder.add(i, condition_builder.get(i).getJmmChild(0));
                    condition_builder.add(i + 2, condition_builder.get(i + 1).getJmmChild(1));
                    i = -1;
                }
            }
        }
        for (int i = 0; i < condition_builder.size(); i++) {
            JmmNode anal = condition_builder.get(i);
            if (Objects.equals(anal.getKind(), "Literal")) {
                if (anal.hasAttribute("id")) {
                    res.append(get_variable(anal.get("id"), local_variables, parameter_variables, classfield_variables, "", true));
                } else {
                    res.append(get_value_from_terminal_literal(anal));
                }
            } else if (Objects.equals(anal.getKind(), "Parenthesis")) {
                res.append("( ").append(binary_ops_handler(local_variables, parameter_variables, classfield_variables, anal.getJmmChild(0))).append(" )");
            } else if (Objects.equals(anal.getKind(), "MethodCall")) {
                JmmNode oi = find_node_kind_child_in_node(anal, "ObjectInstantiation");
                if (oi != null) {
                    res.append("temp_").append(this.temp_n).append(".").append(oi.get("objectName")).append(" :=.").append(oi.get("objectName")).append(" new(").append(oi.get("objectName")).append(").").append(oi.get("objectName")).append(";\n");
                    this.temp_n++;
                    res.append("invokespecial(temp_").append(this.temp_n-1).append(".").append(oi.get("objectName")).append(", \"<init>\").V;\n");
                    res.append("temp_").append(this.temp_n).append(".").append("unknown").append(" :=.").append(binary_ops_handler(local_variables, parameter_variables, classfield_variables, anal.getJmmChild(1))).append(" invokevirtual(temp_").append(this.temp_n-1).append(".").append(oi.get("objectName")).append(",\"").append(anal.get("method")).append("\"");
                    if (oi.getJmmParent().getChildren().size() > 1) {
                        List<JmmNode> arguments = oi.getJmmParent().getChildren();
                        for (int j = 1; j < arguments.size(); j++) {
                            res.append(",").append(binary_ops_handler(local_variables, parameter_variables, classfield_variables, arguments.get(j)));
                        }
                    }
                    res.append(");\n");
                } else {
                    System.out.println("PRECISA DE COISAS");
                    if (anal.getChildren().size() > 1) {
                        List<JmmNode> arguments = anal.getChildren();
                        for (int j = 1; j < arguments.size(); j++) {
                            res.append(", ").append(binary_ops_handler(local_variables, parameter_variables, classfield_variables, arguments.get(j)));
                        }
                    }
                }
                res.append("invokevirtual(").append(anal.getJmmChild(0).get("id")).append(",\"").append(anal.get("method")).append("\", temp_").append(this.temp_n).append(");\n");
            } else {
                res.append(" ").append(anal.get("op")).append(get_type_of_op(anal.get("op"), condition_builder.get(i-1), local_variables, parameter_variables, classfield_variables)).append(" ");
            }
        }
        return res.toString();
    }
    
    private JmmNode find_node_kind_child_in_node(JmmNode node, String kind_look) {
        ArrayList<JmmNode> tmp = new ArrayList<>();
        tmp.add(node);
        while (tmp.size() > 0) {
            if (Objects.equals(tmp.get(0).getKind(), kind_look))
                return tmp.get(0);
            JmmNode removed = tmp.remove(0);
            tmp.addAll(0, removed.getChildren());
        }
        return null;
    }

    private String get_type_of_op(String op, JmmNode prev_node, List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables) {
        switch (op) {
            case "+", "-", "*", "/" -> {
                return "." + get_value_from_terminal_literal(prev_node).split("\\.", 2)[1];
            }
        }
        return ".bool";
    }

    private String get_parameter_variable(List<Symbol> parameterVariables, String var_name) {
        StringBuilder type_var = new StringBuilder(".");
        int i = 1;
        for (Symbol lv : parameterVariables) {
            if (Objects.equals(lv.getName(), var_name)) {
                type_var.append(convert_type(lv.getType()));
                break;
            }
            i++;
        }
        return "$" + i + "." + var_name + type_var;
    }

    private boolean exists_in_variable(List<Symbol> local_variables, String var_name) {
        for (Symbol lv : local_variables) {
            if (Objects.equals(lv.getName(), var_name)) {
                return true;
            }
        }
        return false;
    }

    private String get_variable_type(List<Symbol> variables_list, String var_name) {
        StringBuilder type_var = new StringBuilder(".");
        for (Symbol lv : variables_list) {
            if (Objects.equals(lv.getName(), var_name)) {
                type_var.append(convert_type(lv.getType()));
                break;
            }
        }
        return type_var.toString();
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
        List<Symbol> fields = this.symbol_table.getFields();
        for (Symbol f : fields) {
            res.append(".field private ").append(f.getName()).append(".").append(this.convert_type(f.getType())).append(";\n");
        }
    }
    @Override
    public OllirResult toOllir(JmmSemanticsResult jmmSemanticsResult) {
        this.symbol_table = (SymbolTable) jmmSemanticsResult.getSymbolTable();
        this.root_node = jmmSemanticsResult.getRootNode();
        write_import(this.symbol_table.getSomethingFromTable("import"));
        res.append("\n");
        write_class(this.symbol_table.getSomethingFromTable("class").get(0), this.symbol_table.getMethods());

        return new OllirResult(this.res.toString(), jmmSemanticsResult.getConfig());
    }

    SymbolTable symbol_table;
    JmmNode root_node;

    int temp_n;

    StringBuilder res;

    public OllirParser() {
        this.symbol_table = null;
        this.root_node = null;
        this.temp_n = 0;
        this.res = new StringBuilder();
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
        for (JmmNode statement : method_node.getChildren()) {
            if (!Objects.equals(statement.getKind(), "ReturnType") && !Objects.equals(statement.getKind(), "MethodArgument") && statement.hasAttribute("ollirhelper")) {
                res.append(statement.get("ollirhelper")).append("\n");
            }
        }
    }

    private void method_insides_handler(JmmNode node, List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables) {
        if (node.getNumChildren() != 0) {
            for (JmmNode statement : node.getChildren()) {
                method_insides_handler(statement, local_variables, parameter_variables, classfield_variables);
            }
            switch(node.getKind()) {
                case "ReturnStmt":
                    node.put("ollirhelper", handle_return_statement(node));
                    break;
                case "VarDeclaration":
                    System.out.println("TBI");
                    handle_variable_declaration(node);
                    break;
                case "BinaryOp":
                    node.put("ollirhelper", handle_binary_ops(node));
                    break;
                case "Condition":
                    node.put("ollirhelper", node.getJmmChild(0).get("ollirhelper"));
                    break;
                case "Body", "ElseStmtBody":
                    node.put("ollirhelper", handle_bodies(node, local_variables, parameter_variables, classfield_variables));
                    break;
                case "Assignment":
                    node.put("ollirhelper", handle_assignments(node, local_variables, parameter_variables, classfield_variables));
                    break;
                case "MethodCall":
                    node.put("ollirhelper", handle_method_calls(node, local_variables, parameter_variables, classfield_variables));
                    break;
                case "Parenthesis":
                    node.put("ollirhelper", handle_parenthesis(node, local_variables, parameter_variables, classfield_variables));
                    break;
                case "IfStatement":
                    node.put("ollirhelper", handle_ifs(node, local_variables, parameter_variables, classfield_variables));
                    break;
            }
        } else {
            switch(node.getKind()) {
                case "Type", "Object":
                    break;
                case "Literal":
                    node.put("ollirhelper", handle_literals(node, local_variables, parameter_variables, classfield_variables));
                    break;
                case "ObjectInstantiation":
                    node.put("ollirhelper", handle_object_instantiation(node));
                    node.put("id", node.get("ollirhelper"));
                    break;
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
        return res.toString();
    }

    private String handle_bodies(JmmNode node, List<Symbol> localVariables, List<Symbol> parameterVariables, List<Symbol> classfieldVariables) {
        StringBuilder res = new StringBuilder();
        if (Objects.equals(node.getJmmChild(0).getKind(), "Body") || Objects.equals(node.getJmmChild(0).getKind(), "ElseStmtBody")) {
            res.append(node.getJmmChild(0).get("ollirhelper")).append("\n");
        } else {
            for (JmmNode c : node.getChildren()) {
                res.append(c.get("ollirhelper")).append(";\n");
            }
        }
        return res.toString();
    }

    private String handle_ifs(JmmNode node, List<Symbol> localVariables, List<Symbol> parameterVariables, List<Symbol> classfieldVariables) {
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
        return res.toString();
    }

    private String handle_parenthesis(JmmNode node, List<Symbol> localVariables, List<Symbol> parameterVariables, List<Symbol> classfieldVariables) {
        res.append("temp_").append(this.temp_n).append(".").append(get_var_type_from_name(node.getJmmChild(0).get("ollirhelper"))).append(" :=.").append(get_var_type_from_name(node.getJmmChild(0).get("ollirhelper"))).append(" ").append(node.getJmmChild(0).get("ollirhelper")).append(";\n");
        this.temp_n++;
        return "temp_" + (this.temp_n-1) + "." + get_var_type_from_name(node.getJmmChild(0).get("ollirhelper"));
    }

    private String handle_method_calls(JmmNode node, List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables) {
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
        if (!Objects.equals(node.getJmmParent().getKind(), "Stmt"))
            res.append("temp_").append(this.temp_n).append(".").append(get_return_type_of_method(node.get("method"))).append(" :=.").append(get_return_type_of_method(node.get("method"))).append(" ");
        res.append((!Objects.equals(node.getJmmChild(0).get("id"), "this") && !node.getJmmChild(0).get("id").contains(".") ? "invokestatic(" : "invokevirtual(")).append(node.getJmmChild(0).get("id")).append(",\"").append(node.get("method")).append("\"");
        if (node.getChildren().size() > 1) {
            List<JmmNode> arguments = node.getChildren();
            for (int j = 1; j < arguments.size(); j++) {
                res.append(",");
                res.append(args_is_bin_ops.get(j-1) != -1 ? "temp_" + args_is_bin_ops.get(j-1) + "." + get_var_type_from_name(arguments.get(j).get("ollirhelper")) : arguments.get(j).get("ollirhelper"));
            }
        }
        res.append(").").append(!Objects.equals(node.getJmmChild(0).get("id"), "this") && !node.getJmmChild(0).get("id").contains(".") ? "V" : get_return_type_of_method(node.get("method"))).append(";\n");
        this.temp_n++;
        return !Objects.equals(node.getJmmChild(0).get("id"), "this") && !node.getJmmChild(0).get("id").contains(".") ? "" : "temp_" + (this.temp_n-1) + "." + get_return_type_of_method(node.get("method"));
    }

    private String get_return_type_of_method(String method_name) {
        return this.convert_type(this.symbol_table.getReturnType(method_name));
    }

    private String handle_assignments(JmmNode node, List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables) {
        String variable = "";
        if (exists_in_variable(local_variables, node.get("variable"))) {
            // its a local variable
            variable = get_local_variable(node.get("variable"), local_variables);
        } else if (exists_in_variable(parameter_variables, node.get("variable"))) {
            variable =  get_parameter_variable(node.get("variable"), parameter_variables);
        } else {
            variable =  get_classfield_variable(node.get("variable"), classfield_variables);
        }

        return variable + " :=." + get_var_type_from_name(variable) + " " + node.getJmmChild(0).get("ollirhelper") + ";";
    }

    private String handle_binary_ops(JmmNode condition) {
        ArrayList<JmmNode> condition_builder = new ArrayList<>();
        condition_builder.add(condition);
        for (int i = 0; i < condition_builder.size(); i++) {
            if (Objects.equals(condition_builder.get(i).getKind(), "BinaryOp")) {
                if (!condition_builder.contains(condition_builder.get(i).getJmmChild(0))) {
                    condition_builder.add(i, condition_builder.get(i).getJmmChild(0));
                    condition_builder.add(i + 2, condition_builder.get(i + 1).getJmmChild(1));
                    i = -1;
                }
            }
        }
        String variable = "";
        StringBuilder res = new StringBuilder();
        for (JmmNode n : condition_builder) {
            if (!Objects.equals(n.getKind(), "BinaryOp")) {
                res.append(n.get("ollirhelper"));
                if (Objects.equals(variable, ""))
                    variable = n.get("ollirhelper");
            } else {
                variable = switch (n.get("op")) {
                    case "<", ">", "<=", ">=", "==", "!=" -> ".bool";
                    default -> variable;
                };
                res.append(" ").append(n.get("op")).append(".").append(get_var_type_from_name(variable)).append(" ");
            }
        }
        return res.toString();
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

    private void handle_variable_declaration(JmmNode node) {
        if (node.getNumChildren() > 1) {

        }
    }

    private String handle_object_instantiation(JmmNode node) {
        res.append("temp_").append(this.temp_n).append(".").append(node.get("objectName")).append(" :=.").append(node.get("objectName")).append(" new(").append(node.get("objectName")).append(").").append(node.get("objectName")).append(";\n");
        this.temp_n++;
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
                return get_classfield_variable(variable_name, classfield_variables);
            }
        }
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

    private String get_classfield_variable(String var_name, List<Symbol> classfield_variables) {
        for (Symbol lv : classfield_variables) {
            if (Objects.equals(lv.getName(), var_name)) {
                res.append("temp_").append(this.temp_n).append(".").append(convert_type(lv.getType())).append(" :=.").append(convert_type(lv.getType())).append(" getfield(this, ").append(var_name).append(".").append(convert_type(lv.getType())).append(").").append(convert_type(lv.getType())).append(";\n");
                this.temp_n++;
                return "temp_" + (this.temp_n-1) + "." + convert_type(lv.getType());
            }
        }
        return "error.error";
    }
}
