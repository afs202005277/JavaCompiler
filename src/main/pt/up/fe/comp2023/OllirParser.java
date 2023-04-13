package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
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
        List<Symbol> parameter_variables = this.symbol_table.getParameters(method_name);
        List<Symbol> classfield_variables = this.symbol_table.getFields();

        assert method_node != null;

        return write_method_insides_from_node(method_node, local_variables, parameter_variables, classfield_variables);
    }

    private String write_method_insides_from_node(JmmNode method_node, List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables) {
        StringBuilder res = new StringBuilder();

        assert method_node != null;
        for (JmmNode statement : method_node.getChildren()) {
            switch (statement.getKind()) {
                case "Assignment" -> {
                    if (statement.getChildren().size() > 1) {
                        String var_type = get_only_type_variable(statement.get("id"), local_variables, parameter_variables, classfield_variables);
                        res.append(statement.get("id")).append("[").append(get_attribute_and_type_from_literal(statement.getJmmChild(0), local_variables, parameter_variables, classfield_variables)).append("]").append(var_type).append(" :=").append(var_type).append(" ").append(binary_ops_handler(local_variables, parameter_variables, classfield_variables, statement.getJmmChild(1))).append(";\n");
                    } else {
                        if (exists_in_variable(classfield_variables, statement.get("variable"))) {
                            res.append(get_variable(statement.get("variable"), local_variables, parameter_variables, classfield_variables, get_attribute_and_type_from_literal(statement.getJmmChild(0), local_variables, parameter_variables, classfield_variables), false)).append(";\n");
                        } else {
                            String var = get_variable(statement.get("variable"), local_variables, parameter_variables, classfield_variables, get_attribute_and_type_from_literal(statement.getJmmChild(0), local_variables, parameter_variables, classfield_variables), false);
                            String var_type = get_only_type_variable(statement.get("variable"), local_variables, parameter_variables, classfield_variables);
                            res.append(var).append(" :=").append(var_type).append(" ");
                            if (Objects.equals(statement.getJmmChild(0).getKind(), "BinaryOp")) {
                                res.append(binary_ops_handler(local_variables, parameter_variables, classfield_variables, statement.getJmmChild(0))).append(";\n");
                            } else {
                                res.append(get_attribute_and_type_from_literal(statement.getJmmChild(0), local_variables, parameter_variables, classfield_variables)).append(";\n");
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
                return "getfield(this, " + id + var_type + ", " + value_assignment + ")" + var_type;
            else
                return "putfield(this, " + id + var_type + ", " + value_assignment + ").V";
        }
    }

    private String binary_ops_handler(List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables, JmmNode condition) {
        StringBuilder res = new StringBuilder();
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
                    res.append(get_attribute_and_type_from_literal(anal, local_variables, parameter_variables, classfield_variables));
                }
            } else {
                res.append(" ").append(anal.get("op")).append(get_type_of_op(anal.get("op"), condition_builder.get(i-1), local_variables, parameter_variables, classfield_variables)).append(" ");
            }
        }
        return res.toString();
    }

    private String get_type_of_op(String op, JmmNode prev_node, List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables) {
        switch (op) {
            case "+", "-", "*", "/" -> {
                return "." + get_attribute_and_type_from_literal(prev_node, local_variables, parameter_variables, classfield_variables).split("\\.", 2)[1];
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

    private String get_attribute_and_type_from_literal(JmmNode literal, List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables) {
        if (literal.hasAttribute("bool")) {
            return literal.get("bool") + ".bool";
        } else if (literal.hasAttribute("integer")) {
            return literal.get("integer") + ".i32";
        } else if (literal.hasAttribute("id")) {
            return get_only_type_variable(literal.get("id"), local_variables, parameter_variables, classfield_variables);
        }
        return "unknown.unknown";
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

        System.out.println(ollirCode);
        return new OllirResult(ollirCode, jmmSemanticsResult.getConfig());
    }

    SymbolTable symbol_table;
    JmmNode root_node;

    OllirParser() {
        this.symbol_table = null;
        this.root_node = null;
    }
}
