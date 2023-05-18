package pt.up.fe.comp2023;

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.*;

public class OllirParser implements JmmOptimization {

    private Map<String, String> config;

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
        String method_name = tmp[tmp.length - 1];
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
        List<JmmNode> methods = this.root_node.getJmmChild(this.root_node.getNumChildren() - 1).getChildren();
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

    private void write_fields() {
        List<Symbol> fields = this.symbol_table.getFields();
        for (Symbol f : fields) {
            res.append(".field private ").append(f.getName()).append(".").append(this.convert_type(f.getType())).append(";\n");
        }
    }

    @Override
    public OllirResult toOllir(JmmSemanticsResult jmmSemanticsResult) {
        this.symbol_table = (SymbolTable) jmmSemanticsResult.getSymbolTable();
        this.root_node = jmmSemanticsResult.getRootNode();
        this.config = jmmSemanticsResult.getConfig();
        write_import(this.symbol_table.getSomethingFromTable("import"));
        res.append("\n");
        write_class(this.symbol_table.getSomethingFromTable("class").get(0), this.symbol_table.getMethods());

        OllirResult ollirResult = new OllirResult(this.res.toString(), jmmSemanticsResult.getConfig());

        return ollirResult;
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        if (!(config.containsKey("registerAllocation") && Integer.parseInt(config.get("registerAllocation")) != -1)) {
            return ollirResult;
        }

        ollirResult.getOllirClass().buildCFGs();
        for (int i = 0; i < ollirResult.getOllirClass().getMethods().size(); i++) {
            Report report = optimization_register_allocation(ollirResult.getOllirClass().getMethod(i));
            if (report.getType() == ReportType.ERROR)
                ollirResult.getReports().add(report);
        }
        return ollirResult;
    }

    private Report optimization_register_allocation(Method method) {
        // Iterate through the nodes in reverse topological order
        HashMap<Node, ArrayList<String>> LiveIn_prev = new HashMap<>();
        HashMap<Node, ArrayList<String>> LiveOut_prev = new HashMap<>();

        Node checking_node = method.getEndNode();

        HashMap<Node, ArrayList<String>> LiveInCopy;
        HashMap<Node, ArrayList<String>> LiveOutCopy;

        HashMap<Node, DefAndUse> def_use_table = new HashMap<>();

        do {
            ArrayList<Node> visited = new ArrayList<>();
            LiveInCopy = new HashMap<>(LiveIn_prev);
            LiveOutCopy = new HashMap<>(LiveOut_prev);
            loop_through_nodes(checking_node, LiveIn_prev, LiveOut_prev, visited, method, def_use_table);
        } while (!LiveIn_prev.equals(LiveInCopy) || !LiveOut_prev.equals(LiveOutCopy));

        HashMap<Node, HashSet<String>> LiveOut = convert_arraylist_hashset(LiveOut_prev);

        InterferenceGraph interferenceGraph = new InterferenceGraph();

        // Iterate over each instruction node
        for (Node instruction : method.getInstructions()) {

            // Get the LiveOut set for the current instruction
            HashSet<String> liveOut = LiveOut.get(instruction);
            liveOut.addAll(def_use_table.get(instruction).def);

            // Add variables as nodes to the interference graph
            for (String variable : liveOut) {
                interferenceGraph.addInterferenceGraphNode(new InterferenceGraphNode(variable));
            }

            // Add interference edges between variables in the LiveOut set
            for (String u : liveOut) {
                for (String v : liveOut) {
                    if (!u.equals(v)) {
                        interferenceGraph.addEdge(u, v);
                    }
                }
            }
        }

        int colorsNeeded = 0;
        if (Integer.parseInt(config.get("registerAllocation")) == 0) {
            int i = 1;
            colorsNeeded = interferenceGraph.colorGraph(i);
            while (colorsNeeded == -1 && interferenceGraph.numNodes() != 0) {
                i++;
                colorsNeeded = interferenceGraph.colorGraph(i);
            }

            if (interferenceGraph.numNodes() == 0)
                colorsNeeded = 0;
        } else {
            if (interferenceGraph.numNodes() != 0)
                colorsNeeded = interferenceGraph.colorGraph(Integer.parseInt(config.get("registerAllocation")));
        }

        if (colorsNeeded == -1) {
            int i = 1;
            colorsNeeded = interferenceGraph.colorGraph(i);
            while (colorsNeeded == -1 && interferenceGraph.numNodes() != 0) {
                i++;
                colorsNeeded = interferenceGraph.colorGraph(i);
            }

            String report_message = ("Method " + method.getMethodName() + " needs a minimum of " + i + " registers.");
            return new Report(ReportType.ERROR, Stage.OPTIMIZATION, -1, report_message);
        }

        //interferenceGraph.visualizeGraph();

        System.out.print("COLOR GRAPHING SOLUTION: ");
        System.out.println(colorsNeeded);

        ArrayList<String> colors = new ArrayList<>();
        colors.add("this_color");

        Set<InterferenceGraphNode> graph_nodes = interferenceGraph.getNodes();


        for (Map.Entry<String, Descriptor> entry : method.getVarTable().entrySet()) {
            if (!entry.getValue().getScope().equals(VarScope.LOCAL)) {
                for (InterferenceGraphNode n : graph_nodes) {
                    if (Objects.equals(n.getRegister(), entry.getKey())) {
                        if (!colors.contains(n.getColor()))
                            colors.add(n.getColor());
                        break;
                    }
                }
            }
        }


        for (Map.Entry<String, Descriptor> entry : method.getVarTable().entrySet()) {
            for (InterferenceGraphNode graph_node : graph_nodes) {
                if (Objects.equals(graph_node.getRegister(), entry.getKey())) {

                    if (!colors.contains(graph_node.getColor()))
                        colors.add(graph_node.getColor());

                    entry.getValue().setVirtualReg(colors.indexOf(graph_node.getColor()));
                    method.getVarTable().put(entry.getKey(), entry.getValue());
                }
            }
        }

        return new Report(ReportType.LOG, Stage.OPTIMIZATION, -1, "Optimization complete on method " + method.getMethodName());
    }

    private HashMap<Node, HashSet<String>> convert_arraylist_hashset(HashMap<Node, ArrayList<String>> a) {
        HashMap<Node, HashSet<String>> res = new HashMap<>();
        for (Map.Entry<Node, ArrayList<String>> entry : a.entrySet()) {
            res.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        return res;
    }

    private void loop_through_nodes(Node checking_node, HashMap<Node, ArrayList<String>> LiveIn, HashMap<Node, ArrayList<String>> LiveOut, ArrayList<Node> visited, Method method, HashMap<Node, DefAndUse> def_use_table) {
        if (checking_node.getNodeType().name().equals("BEGIN")) {

            for (Element m : method.getParams()) {
                AssignInstruction temp_arg = new AssignInstruction(m, m.getType(), new SingleOpInstruction(m));
                visited.add(temp_arg);
                loop_through_nodes(temp_arg, LiveIn, LiveOut, visited, method, def_use_table);
            }

        } else if (checking_node.getNodeType().name().equals("END")) {

            for (Node node : new HashSet<>(checking_node.getPredecessors())) {
                loop_through_nodes(node, LiveIn, LiveOut, visited, method, def_use_table);
            }

        } else if (!visited.contains(checking_node)) {

            DefAndUse tmp = def_and_use_variables((Instruction) checking_node);
            def_use_table.put(checking_node, tmp);


            // LiveOut - U ( in[n] | S € succ[n] )
            if (!LiveOut.containsKey(checking_node)) {
                LiveOut.put(checking_node, new ArrayList<>());
            }
            for (int i = 0; i < checking_node.getSuccessors().size(); i++) {
                if (LiveIn.containsKey(checking_node.getSuccessors().get(i))) {
                    if (LiveOut.containsKey(checking_node)) {
                        LiveOut.get(checking_node).addAll(LiveIn.get(checking_node.getSuccessors().get(i)));
                    }
                }
            }

            // LiveIn - use[n] U ( out[n] - def[n] )
            if (LiveIn.containsKey(checking_node)) {
                LiveIn.get(checking_node).addAll(tmp.use);
            } else {
                LiveIn.put(checking_node, tmp.use);
            }
            LiveIn.get(checking_node).addAll(all_but_select_few(LiveOut.get(checking_node), tmp.def));

            visited.add(checking_node);
            for (Node node : checking_node.getPredecessors()) {
                loop_through_nodes(node, LiveIn, LiveOut, visited, method, def_use_table);
            }
        }
    }

    private ArrayList<String> all_but_select_few(ArrayList<String> total, ArrayList<String> removed) {
        ArrayList<String> tmp = new ArrayList<>();
        for (int i = 0; i < total.size(); i++) {
            if (!removed.contains(total.get(i)))
                tmp.add(total.get(i));
        }
        return tmp;
    }

    static class DefAndUse {
        public ArrayList<String> def = new ArrayList<>();
        public ArrayList<String> use = new ArrayList<>();
    }

    private DefAndUse def_and_use_variables(Instruction instruction) {
        DefAndUse tmp = new DefAndUse();
        switch (instruction.getInstType()) {
            case CALL -> {
                if (!((CallInstruction) instruction).getInvocationType().name().contains("static") && !Objects.equals(((Operand) ((CallInstruction) instruction).getFirstArg()).getName(), "this"))
                    tmp.use.add(((Operand) ((CallInstruction) instruction).getFirstArg()).getName());
                if (((CallInstruction) instruction).getListOfOperands() != null)
                    for (Element m : ((CallInstruction) instruction).getListOfOperands())
                        if (!m.isLiteral()) {
                            if (m instanceof ArrayOperand arrayOperand) {
                                for (Element element : arrayOperand.getIndexOperands()) {
                                    if (!element.isLiteral())
                                        tmp.use.add(((Operand) element).getName());
                                }
                                tmp.use.add(arrayOperand.getName());
                            } else {
                                if (!m.isLiteral())
                                    tmp.use.add(((Operand) m).getName());
                            }

                        }
                return tmp;
            }
            case GOTO -> {
                return tmp;
            }
            case NOPER -> {
                Element m = ((SingleOpInstruction) instruction).getSingleOperand();
                if (m instanceof ArrayOperand arrayOperand) {
                    for (Element element : arrayOperand.getIndexOperands()) {
                        if (!element.isLiteral())
                            tmp.use.add(((Operand) element).getName());
                    }
                    tmp.use.add(arrayOperand.getName());
                } else {
                    if (!m.isLiteral())
                        tmp.use.add(((Operand) m).getName());
                }
                return tmp;
            }
            case ASSIGN -> {
                if (((AssignInstruction) instruction).getDest() instanceof ArrayOperand arrayOperand) {
                    for (Element element : arrayOperand.getIndexOperands()) {
                        if (!element.isLiteral())
                            tmp.use.add(((Operand) element).getName());
                    }
                    tmp.use.add(arrayOperand.getName());
                } else {
                    if (!((AssignInstruction) instruction).getDest().isLiteral())
                        tmp.def.add(((Operand) ((AssignInstruction) instruction).getDest()).getName());
                }
                tmp.use.addAll(def_and_use_variables(((AssignInstruction) instruction).getRhs()).use);
                return tmp;
            }
            case BRANCH -> {
                CondBranchInstruction element = ((CondBranchInstruction) instruction);
                DefAndUse a = def_and_use_variables(element.getCondition());
                tmp.def.addAll(a.def);
                tmp.use.addAll(a.use);
                return tmp;
            }
            case RETURN -> {
                Element m = ((ReturnInstruction) instruction).getOperand();
                if (m instanceof ArrayOperand arrayOperand) {
                    for (Element element : arrayOperand.getIndexOperands()) {
                        if (element != null && !element.isLiteral())
                            tmp.use.add(((Operand) element).getName());
                    }
                    tmp.use.add(arrayOperand.getName());
                } else {
                    if (m != null && !m.isLiteral())
                        tmp.use.add(((Operand) m).getName());
                }
                return tmp;
            }
            case GETFIELD -> {
                Element m = ((GetFieldInstruction) instruction).getSecondOperand();
                if (m instanceof ArrayOperand arrayOperand) {
                    for (Element element : arrayOperand.getIndexOperands()) {
                        if (element != null && !element.isLiteral())
                            tmp.use.add(((Operand) element).getName());
                    }
                    tmp.use.add(arrayOperand.getName());
                } else {
                    if (m != null && !m.isLiteral())
                        tmp.use.add(((Operand) m).getName());
                }
                return tmp;
            }
            case PUTFIELD -> {
                Element assignee = ((PutFieldInstruction) instruction).getSecondOperand();
                if (assignee instanceof ArrayOperand arrayOperand) {
                    for (Element element : arrayOperand.getIndexOperands()) {
                        if (element != null && !element.isLiteral())
                            tmp.def.add(((Operand) element).getName());
                    }
                    tmp.use.add(arrayOperand.getName());
                } else {
                    if (assignee != null && !assignee.isLiteral())
                        tmp.def.add(((Operand) assignee).getName());
                }
                Element assigned = ((PutFieldInstruction) instruction).getThirdOperand();
                if (assigned instanceof ArrayOperand arrayOperand) {
                    for (Element element : arrayOperand.getIndexOperands()) {
                        if (element != null && !element.isLiteral())
                            tmp.use.add(((Operand) element).getName());
                    }
                    tmp.use.add(arrayOperand.getName());
                } else {
                    if (assigned != null && !assigned.isLiteral())
                        tmp.use.add(((Operand) assigned).getName());
                }
                return tmp;
            }
            case UNARYOPER -> {
                Element m = ((UnaryOpInstruction) instruction).getOperand();
                if (m instanceof ArrayOperand arrayOperand) {
                    for (Element element : arrayOperand.getIndexOperands()) {
                        if (!element.isLiteral())
                            tmp.use.add(((Operand) element).getName());
                    }
                    tmp.use.add(arrayOperand.getName());
                } else {
                    if (!m.isLiteral())
                        tmp.use.add(((Operand) m).getName());
                }
                return tmp;
            }
            case BINARYOPER -> {
                BinaryOpInstruction binaryOpInstruction = ((BinaryOpInstruction) instruction);
                if (!binaryOpInstruction.getLeftOperand().isLiteral())
                    tmp.use.add(((Operand) binaryOpInstruction.getLeftOperand()).getName());
                if (!binaryOpInstruction.getRightOperand().isLiteral())
                    tmp.use.add(((Operand) binaryOpInstruction.getRightOperand()).getName());
                return tmp;
            }
            default -> {
                System.out.println("UNKNOWN INSTRUCTION TYPE IN OPTIMIZER");
                return new DefAndUse();
            }
        }
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
        String method_name = tmp[tmp.length - 1];

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
            switch (node.getKind()) {
                case "ReturnStmt" -> node.put("ollirhelper", handle_return_statement(node));
                case "VarDeclaration" ->
                        node.put("ollirhelper", handle_variable_declaration(node, local_variables, parameter_variables, classfield_variables));
                case "BinaryOp" -> node.put("ollirhelper", handle_binary_ops(node));
                case "Condition" -> {
                    node.put("ollirhelper", node.getJmmChild(0).get("ollirhelper"));
                    handle_before_hand(node, new StringBuilder());
                }
                case "Body", "ElseStmtBody" -> node.put("ollirhelper", handle_bodies(node));
                case "Assignment" ->
                        node.put("ollirhelper", handle_assignments(node, local_variables, parameter_variables, classfield_variables));
                case "MethodCall" ->
                        node.put("ollirhelper", handle_method_calls(node, local_variables, parameter_variables, classfield_variables));
                case "Parenthesis" -> node.put("ollirhelper", handle_parenthesis(node));
                case "IfStatement" -> node.put("ollirhelper", handle_ifs(node));
                case "Stmt" -> handle_before_hand(node, new StringBuilder());
                case "WhileLoop" -> node.put("ollirhelper", handle_whiles(node));
                case "Length" -> node.put("ollirhelper", handle_lengths(node));
                case "ArrayIndex" -> node.put("ollirhelper", handle_array_index(node));
                case "UnaryOp" -> node.put("ollirhelper", handle_unary_ops(node));
                case "ArrayDeclaration" -> node.put("ollirhelper", handle_array_declaration(node));
            }
        } else {
            switch (node.getKind()) {
                case "Type", "Object" -> handle_before_hand(node, new StringBuilder());
                case "Literal", "LiteralS" ->
                        node.put("ollirhelper", handle_literals(node, local_variables, parameter_variables, classfield_variables));
                case "ObjectInstantiation" -> {
                    node.put("ollirhelper", handle_object_instantiation(node));
                    node.put("id", node.get("ollirhelper"));
                }
            }
        }
    }

    private String handle_unary_ops(JmmNode node) {
        StringBuilder res = new StringBuilder();
        res.append("temp_").append(this.temp_n).append(".bool :=.bool ").append(node.get("op") + ".bool " + node.getJmmChild(0).get("ollirhelper")).append(";\n");
        this.temp_n++;
        handle_before_hand(node, res);
        return "temp_" + (this.temp_n-1) + ".bool";
    }

    private String handle_array_index(JmmNode node) {
        StringBuilder res = new StringBuilder();
        String argument = node.getJmmChild(0).get("ollirhelper").replace(node.getJmmChild(0).get("id") + ".array", "");
        String variable;
        if (argument.contains("$")) {
            variable = argument.split("\\$[0-9]+\\.")[1];
            argument = argument.split(variable)[0];
        } else {
            variable = argument;
            argument = "";
        }

        if (Objects.equals(node.getJmmChild(1).getKind(), "Literal")) {
            res.append("temp_").append(this.temp_n).append(".").append(convert_type(new Type(node.getJmmChild(1).get("varType"), false))).append(" :=.").append(convert_type(new Type(node.getJmmChild(1).get("varType"), false))).append(" ").append(node.getJmmChild(1).get("ollirhelper")).append(";\n");
            this.temp_n++;
            node.getJmmChild(1).put("ollirhelper", "temp_" + (this.temp_n - 1) + "." + convert_type(new Type(node.getJmmChild(1).get("varType"), false)));
        }
        res.append("temp_").append(this.temp_n).append(variable).append(" :=").append(variable).append(" ").append(argument + node.getJmmChild(0).get("id")).append("[");
        res.append(node.getJmmChild(1).get("ollirhelper")).append("]").append(variable).append(";");
        this.temp_n++;
        handle_before_hand(node, res);
        return "temp_" + (this.temp_n - 1) + variable;
    }

    private String handle_array_declaration(JmmNode node) {
        String var_type = convert_type(new Type(node.get("varType"), true));
        handle_before_hand(node, new StringBuilder());
        return node.get("variable") + "." + var_type + " :=." + var_type + " new(array, " + node.getJmmChild(0).get("ollirhelper") + ".i32)." + var_type + ";";
    }

    private String handle_lengths(JmmNode node) {
        StringBuilder res = new StringBuilder();
        res.append("temp_").append(this.temp_n).append(".i32 :=.i32 arraylength(").append(node.getJmmChild(0).get("ollirhelper")).append(").i32;");
        this.temp_n++;
        handle_before_hand(node, res);
        return "temp_" + (this.temp_n - 1) + ".i32";
    }

    private String handle_whiles(JmmNode node) {
        StringBuilder res = new StringBuilder();
        res.append("loopstart").append(temp_n).append(":\n");
        temp_n++;
        res.append(node.getJmmChild(0).get("beforehand"));
        res.append("if (").append(node.getJmmChild(0).get("ollirhelper")).append(") goto whilestart").append(temp_n).append(";\n");
        temp_n++;
        res.append("goto loopend").append(temp_n).append(";\n");
        temp_n++;
        res.append("whilestart").append(temp_n - 2).append(":\n");
        if (Objects.equals(node.getJmmChild(1).getKind(), "Body")) {
            for (JmmNode child : node.getJmmChild(1).getChildren()) {
                res.append(child.get("beforehand"));
                res.append(child.get("ollirhelper"));
            }
        } else {
            res.append(node.getJmmChild(1).get("beforehand"));
            res.append(node.getJmmChild(1).get("ollirhelper"));
        }
        res.append("\ngoto loopstart").append(temp_n - 3).append(";\n");
        res.append("loopend").append(temp_n - 1).append(":\n");

        return res.toString();
    }

    private String handle_return_statement(JmmNode node) {
        StringBuilder res = new StringBuilder();
        JmmNode argument = node.getJmmChild(0);
        if (Objects.equals(argument.getKind(), "Literal")) {
            res.append("ret").append(".").append(get_var_type_from_name(node.getJmmChild(0).get("ollirhelper"))).append(" ").append(node.getJmmChild(0).get("ollirhelper")).append(";\n");
        } else {
            res.append("temp_").append(this.temp_n).append(".").append(get_var_type_from_name(argument.get("ollirhelper"))).append(" :=.").append(get_var_type_from_name(argument.get("ollirhelper"))).append(" ").append(argument.get("ollirhelper")).append(";\n");
            this.temp_n++;
            res.append("ret").append(".").append(get_var_type_from_name(node.getJmmChild(0).get("ollirhelper"))).append(" ").append("temp_").append(this.temp_n - 1).append(".").append(get_var_type_from_name(argument.get("ollirhelper"))).append(";\n");
        }
        handle_before_hand(node, new StringBuilder());
        return res.toString();
    }

    private String handle_bodies(JmmNode node) {
        StringBuilder res = new StringBuilder();
        if (Objects.equals(node.getJmmChild(0).getKind(), "Body") || Objects.equals(node.getJmmChild(0).getKind(), "ElseStmtBody")) {
            res.append(node.getJmmChild(0).get("ollirhelper")).append("\n");
        } else {
            for (JmmNode c : node.getChildren()) {
                res.append(c.get("ollirhelper"));
                if (!c.get("ollirhelper").contains(";"))
                    res.append(";\n");
            }
        }
        handle_before_hand(node, new StringBuilder());
        return res.toString();
    }

    private String handle_ifs(JmmNode node) {
        StringBuilder res = new StringBuilder();
        res.append("if (").append(node.getJmmChild(0).get("ollirhelper")).append(") goto ifstart").append(temp_n).append(";\n");
        temp_n++;
        if (node.getNumChildren() == 3) {
            res.append("goto else").append(temp_n).append(";\n");
            temp_n++;
        } else {
            res.append("goto endif").append(temp_n).append(";\n");
            temp_n++;
        }
        res.append("ifstart").append(temp_n - 2).append(":\n");
        res.append(node.getJmmChild(1).get("beforehand"));
        res.append(node.getJmmChild(1).get("ollirhelper"));
        if (node.getNumChildren() == 3) {
            res.append("goto endif").append(temp_n).append(";\nelse").append(temp_n - 1).append(":\n");
            res.append(node.getJmmChild(2).get("beforehand"));
            res.append(node.getJmmChild(2).get("ollirhelper"));
        }
        res.append("endif");
        if (node.getNumChildren() == 2) {
            res.append(temp_n - 1);
        } else {
            res.append(temp_n);
            temp_n++;
        }
        res.append(":\n");
        node.put("beforehand", node.getJmmChild(0).get("beforehand"));
        return res.toString();
    }

    private String handle_parenthesis(JmmNode node) {
        node.replace(node.getJmmChild(0));
        return "";
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
                    tmp_var = get_parameter_variable(tmp_var, parameter_variables);
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
            variable = get_parameter_variable(node.getJmmChild(0).get("id"), parameter_variables);
        } else if (exists_in_variable(classfield_variables, node.getJmmChild(0).get("id"))) {
            is_variable = true;
            variable = get_classfield_variable(node.getJmmChild(0), node.getJmmChild(0).get("id"), classfield_variables);
        }

        res.append((!Objects.equals(node.getJmmChild(0).get("id"), "this") && !is_variable && !node.getJmmChild(0).get("ollirhelper").contains("temp_") ? "invokestatic(" + node.getJmmChild(0).get("id") : "invokevirtual(" + node.getJmmChild(0).get("id") + (!Objects.equals(node.getJmmChild(0).get("id"), "this") && !node.getJmmChild(0).get("ollirhelper").contains("temp_") ? "." + get_var_type_from_name(variable) : ""))).append(",\"").append(node.get("method")).append("\"");
        if (node.getChildren().size() > 1) {
            List<JmmNode> arguments = node.getChildren();
            for (int j = 1; j < arguments.size(); j++) {
                res.append(",");
                res.append(args_is_bin_ops.get(j - 1) != -1 ? "temp_" + args_is_bin_ops.get(j - 1) + "." + get_var_type_from_name(arguments.get(j).get("ollirhelper")) : arguments.get(j).get("ollirhelper"));
            }
        }
        res.append(").").append(!Objects.equals(node.getJmmChild(0).get("id"), "this") && !is_variable && !node.getJmmChild(0).get("ollirhelper").contains("temp_") ? "V" : (Objects.equals(get_return_type_of_method(node.get("method")), "unknown") ? get_var_type_from_name(tmp_var) : get_return_type_of_method(node.get("method")))).append(";\n");
        if (Objects.equals(node.getJmmParent().getKind(), "Stmt")) {
            handle_before_hand(node, new StringBuilder());
            node.getJmmParent().put("ollirhelper", res.toString());
        } else
            handle_before_hand(node, res);

        return !Objects.equals(node.getJmmChild(0).get("id"), "this") && !is_variable && !node.getJmmChild(0).get("ollirhelper").contains("temp_") ? "" : "temp_" + (this.temp_n - 1) + "." + (Objects.equals(get_return_type_of_method(node.get("method")), "unknown") ? get_var_type_from_name(tmp_var) : get_return_type_of_method(node.get("method")));
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
        if (node.getNumChildren() != 1) {
            // it is an array assignment
            StringBuilder res = new StringBuilder();
            res.append("temp_").append(this.temp_n).append(".i32 :=.i32 ").append(node.getJmmChild(0).get("ollirhelper")).append(";");
            this.temp_n++;
            String variable;
            if (exists_in_variable(local_variables, node.get("id"))) {
                // its a local variable
                variable = get_local_variable(node.get("id"), local_variables);
                variable = variable.split("array")[1];
                handle_before_hand(node, res);
                return node.get("id") + "[temp_" + (this.temp_n - 1) + ".i32]" + variable + " :=" + variable + " " + node.getJmmChild(1).get("ollirhelper") + ";";
            } else if (exists_in_variable(parameter_variables, node.get("id"))) {
                variable = get_parameter_variable(node.get("id"), parameter_variables);
                String argument = (variable.contains("$") ? variable.split(node.get("id"))[0] : "");
                variable = variable.split("array")[1];
                handle_before_hand(node, res);
                return argument + node.get("id") + "[temp_" + (this.temp_n - 1) + ".i32]" + variable + " :=" + variable + " " + node.getJmmChild(1).get("ollirhelper") + ";";
            } else {
                variable = get_classfield_variable(node, node.get("id"), classfield_variables);
                String var_type = variable.split("array")[1];
                return variable + " :=" + var_type + " " + node.getJmmChild(1).get("ollirhelper") + ";";
            }

        } else {
            String array = "";
            if (Objects.equals(node.getJmmChild(0).getKind(), "ArrayIndex")) {
                String[] tmp = node.getJmmChild(0).get("beforehand").split(" ");
                array = tmp[tmp.length - 1];
                ArrayList<String> tmp2 = new ArrayList<>(List.of(String.join(" ", tmp).split("\n")));
                tmp2.remove(tmp2.size() - 1);
                tmp = tmp2.toArray(new String[0]);
                node.getJmmChild(0).put("beforehand", String.join(" ", tmp));
            }

            String variable;
            if (exists_in_variable(local_variables, node.get("variable"))) {
                // its a local variable
                variable = get_local_variable(node.get("variable"), local_variables);
            } else if (exists_in_variable(parameter_variables, node.get("variable"))) {
                variable = get_parameter_variable(node.get("variable"), parameter_variables);
            } else {
                variable = get_classfield_variable(node, node.get("variable"), classfield_variables);
                handle_before_hand(node, new StringBuilder());
                return "putfield(this, " + node.get("variable") + "." + get_var_type_from_name(variable) + ", " + node.getJmmChild(0).get("ollirhelper") + ").V;";
            }

            if (node.getJmmChild(0).get("beforehand").split("\n").length == 1 && !Objects.equals(node.getJmmChild(0).get("beforehand"), "")) {
                ArrayList<String> tmp = new ArrayList<>(List.of(node.getJmmChild(0).get("beforehand").split(" ")));
                tmp.remove(0);
                tmp.remove(0);
                handle_before_hand(node, new StringBuilder());
                return variable + " :=." + get_var_type_from_name(variable) + " " + (Objects.equals(array, "") ? String.join(" ", tmp.toArray(new String[0])) : array);
            }

            handle_before_hand(node, new StringBuilder());
            return variable + " :=." + get_var_type_from_name(variable) + " " + (Objects.equals(array, "") ? node.getJmmChild(0).get("ollirhelper") + ";" : array);
        }
    }

    private String handle_binary_ops(JmmNode condition) {

        StringBuilder res_beforehand = new StringBuilder();
        Type tmp = new Type(condition.get("varType"), false);
        res_beforehand.append("temp_").append(this.temp_n).append(".").append(convert_type(tmp)).append(" :=.").append(convert_type(tmp)).append(" ");
        this.temp_n++;
        String variable = convert_type(tmp);
        variable = switch (condition.get("op")) {
            case "<", ">", "<=", ">=", "==", "!=" -> "bool";
            default -> variable;
        };
        res_beforehand.append(condition.getJmmChild(0).get("ollirhelper")).append(" ").append(condition.get("op")).append(".").append(variable).append(" ").append(condition.getJmmChild(1).get("ollirhelper")).append(";\n");
        handle_before_hand(condition, res_beforehand);
        return "temp_" + (this.temp_n - 1) + "." + convert_type(tmp);
    }

    private String get_var_type_from_name(String variable) {
        String var_type = "";
        String[] splitted = variable.split("\\.");
        if (variable.contains("array")) {
            var_type += "array.";
        }
        var_type += splitted[splitted.length - 1];
        return var_type;
    }

    private String handle_variable_declaration(JmmNode node, List<Symbol> local_variables, List<Symbol> parameter_variables, List<Symbol> classfield_variables) {
        if (node.getNumChildren() > 1) {
            List<JmmNode> stat_child = node.getChildren();
            String var_name;

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
                contents[contents.length - 1] = contents[contents.length - 1].substring(0, contents[contents.length - 1].length() - 1);

                res.append(var_name).append(var_type).append(" :=").append(var_type).append(" new(array, ").append(contents.length).append(".i32)").append(var_type).append(";\n");

                String type_var_no_array = var_type.split("\\.")[2];
                int i = 0;
                for (String c : contents) {
                    res.append("i.i32 :=.i32 ").append(i).append(".i32;\n");
                    res.append(var_name).append("[").append("i").append(".i32].").append(type_var_no_array).append(" :=.").append(type_var_no_array).append(" ").append(c).append(".").append(type_var_no_array).append(";\n");
                    i++;
                }
                handle_before_hand(node, new StringBuilder());
                return res.toString();
            } else {
                handle_before_hand(node, new StringBuilder());
                return node.getJmmChild(1).get("ollirhelper");
            }
        } else if (Objects.equals(node.get("isArray"), "true")) {
            String res = node.get("variableName") + convert_type(new Type(node.get("varType"), true)) + " :=" + convert_type(new Type(node.get("varType"), true)) + " new(array, " + "contents-length" + ".i32)" + convert_type(new Type(node.get("varType"), true)) + ";\n";
            node.put("ollirhelper", res);
        }
        handle_before_hand(node, new StringBuilder());
        return "";
    }

    private String handle_object_instantiation(JmmNode node) {
        StringBuilder res = new StringBuilder();
        res.append("temp_").append(this.temp_n).append(".").append(node.get("objectName")).append(" :=.").append(node.get("objectName")).append(" new(").append(node.get("objectName")).append(").").append(node.get("objectName")).append(";\n");
        this.temp_n++;
        res.append("invokespecial(").append("temp_").append(this.temp_n - 1).append(".").append(node.get("objectName")).append(",\"<init>\").V;\n");
        handle_before_hand(node, res);
        return "temp_" + (this.temp_n - 1) + "." + node.get("objectName");
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
        handle_before_hand(node, new StringBuilder());
        return get_value_from_terminal_literal(node);
    }

    private String get_value_from_terminal_literal(JmmNode literal) {
        if (literal.hasAttribute("bool")) {
            return (Objects.equals(literal.get("bool"), "true") ? "1" : "0") + ".bool";
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
            name = name.substring(0, name.length() - 2);
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
                return "temp_" + (this.temp_n - 1) + "." + convert_type(lv.getType());
            }
        }
        handle_before_hand(node, res);
        return "error.error";
    }
}
