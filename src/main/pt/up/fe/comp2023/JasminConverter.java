package pt.up.fe.comp2023;

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class JasminConverter implements pt.up.fe.comp.jmm.jasmin.JasminBackend {

    private final HashMap<String, Integer> instructionToCost = new HashMap<>() {{
        put("bipush", 1);
        put("sipush", 1);
        put("iconst_m1", 1);
        put("iconst_0", 1);
        put("iconst_1", 1);
        put("iconst_2", 1);
        put("iconst_3", 1);
        put("iconst_4", 1);
        put("iconst_5", 1);
        put("ldc", 1);
        put("iload", 1);
        put("iload_0", 1);
        put("iload_1", 1);
        put("iload_2", 1);
        put("iload_3", 1);
        put("aload", 1);
        put("aload_0", 1);
        put("aload_1", 1);
        put("aload_2", 1);
        put("aload_3", 1);
        put("istore", -1);
        put("istore_0", -1);
        put("istore_1", -1);
        put("istore_2", -1);
        put("istore_3", -1);
        put("astore", -1);
        put("astore_0", -1);
        put("astore_1", -1);
        put("astore_2", -1);
        put("astore_3", -1);
        put("iastore", -3);
        put("aastore", -3);
        put("iaload", -2 + 1);
        put("aaload", -2 + 1);
        put("new", 1);
        put("pop", -1);
        put("newarray", -1 + 1);
        put("isub", -2 + 1);
        put("iadd", -2 + 1);
        put("iand", -2 + 1);
        put("imul", -2 + 1);
        put("idiv", -2 + 1);
        put("ior", -2 + 1);
        put("ixor", -2 + 1);
        put("iflt", -1);
        put("ifgt", -1);
        put("ifeq", -1);
        put("ifne", -1);
        put("ifle", -1);
        put("ifge", -1);
        put("goto", 0);
        put("ireturn", -1);
        put("areturn", -1);
        put("return", -1);
        put("arraylength", -1 + 1);
        put("putfield", -2);
        put("getfield", -1 + 1);
        put("putstatic", -1);
        put("getstatic", 1);
        put("andb", -2 + 1);
        put("orb", -2 + 1);
        put("iinc", 0);
    }};
    private long label = 0;

    private Operand dest;
    private static final HashMap<String, String> typeToDescriptor = new HashMap<>() {{
        put("BOOLEAN", "Z");
        put("INT32", "I");
        put("STRING", "Ljava/lang/String;");
        put("VOID", "V");
    }};

    private int computeStackLimit(String jasminCode, HashMap<String, Method> methods) {
        int currentStackSize = 0, maxStackSize = 0;

        for (String fullInstruction : jasminCode.split("\n")) {
            if (fullInstruction.isBlank())
                continue;
            String[] instructionParts = fullInstruction.split(" ");
            String instruction = fullInstruction.split(" ")[0];
            Integer cost = instructionToCost.get(instruction);
            if (cost != null) {
                currentStackSize += cost;
            } else {
                if (instruction.contains("invoke")) {
                    Method method = methods.get(instructionParts[1]);
                    if (method != null) {
                        currentStackSize -= method.getParams().size();
                        currentStackSize += method.getReturnType().getTypeOfElement().name().equals("VOID") ? 0 : 1;
                    } else {
                        currentStackSize -= getNumArgs(instructionParts[1]);
                        currentStackSize += instructionParts[1].substring(instructionParts[1].indexOf(')') + 1).startsWith("V") ? 0 : 1;
                    }
                } else {
                    System.out.println("UNKNOWN INSTRUCTION: " + fullInstruction);
                }
            }
            if (currentStackSize > maxStackSize)
                maxStackSize = currentStackSize;
        }
        return maxStackSize;
    }

    public static boolean containsRegex(String str, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(str);
        return matcher.find();
    }

    public static int getNumArgs(String methodSig) {
        int numArgs = 0;
        String regexPattern = "L[^;]+?;";

        methodSig = methodSig.substring(methodSig.indexOf('(') + 1, methodSig.indexOf(')'));

        String tmpMethodSig = methodSig;
        while (containsRegex(tmpMethodSig, regexPattern)) {
            tmpMethodSig = tmpMethodSig.replaceFirst(regexPattern, "");
            numArgs++;
        }
        for (int i = 0; i < methodSig.length(); i++) {
            char c = methodSig.charAt(i);
            if (Character.isUpperCase(c)) {
                numArgs++;
            }
        }

        return numArgs;
    }

    private String handleType(Type type, String suffix) {
        if (suffix.contains("load ") || suffix.contains("store ")) {
            String indexStr = suffix.substring(suffix.indexOf(' ') + 1);
            int index = Integer.parseInt(indexStr);
            if (index <= 3)
                suffix = suffix.replace(" ", "_");
        }

        StringBuilder jasminCode = new StringBuilder();
        switch (type.getTypeOfElement().name()) {
            case "THIS", "ARRAYREF", "STRING", "OBJECTREF" -> jasminCode.append("a").append(suffix);
            case "INT32", "BOOLEAN" -> jasminCode.append("i").append(suffix);
            case "VOID" -> jasminCode.append(suffix);
        }
        return jasminCode.toString();
    }

    private String dispatcher(Instruction instruction, HashMap<String, Descriptor> varTable, List<String> methods, List<String> imports, String parentClass) {
        StringBuilder jasminCode = new StringBuilder();
        switch (instruction.getInstType()) {
            case CALL ->
                    jasminCode.append(processCall((CallInstruction) instruction, varTable, methods, imports, parentClass));
            case GOTO -> jasminCode.append(processGoTo((GotoInstruction) instruction));
            case NOPER -> jasminCode.append(processNoper((SingleOpInstruction) instruction, varTable));
            case ASSIGN ->
                    jasminCode.append(processAssign((AssignInstruction) instruction, varTable, methods, imports, parentClass));
            case BRANCH -> jasminCode.append(processBranch((CondBranchInstruction) instruction, varTable));
            case RETURN -> jasminCode.append(processReturn((ReturnInstruction) instruction, varTable));
            case GETFIELD -> jasminCode.append(processGetField((GetFieldInstruction) instruction, varTable));
            case PUTFIELD -> jasminCode.append(processPutField((PutFieldInstruction) instruction, varTable));
            case UNARYOPER -> jasminCode.append(processUnaryOp((UnaryOpInstruction) instruction, varTable));
            case BINARYOPER -> jasminCode.append(processBinaryOp((BinaryOpInstruction) instruction, varTable));
            default -> jasminCode.append("UNKNOWN INSTRUCTION TYPE");
        }
        return jasminCode.toString();
    }

    private String outputMethodId(String methodName, List<Element> args, Type returnType) {
        Method method = new Method(new ClassUnit());
        method.setMethodName(methodName.replace("\"", ""));
        if (args != null) {
            for (Element arg : args) {
                method.addParam(arg);
            }
        }
        method.setReturnType(returnType);
        return outputMethodId(method);
    }

    private String outputMethodId(Method method) {
        StringBuilder code = new StringBuilder();
        if (method.isConstructMethod()) {
            code.append("<init>");
        } else {
            code.append(method.getMethodName());
        }
        code.append("(");
        for (Element element : method.getParams()) {
            Type type = element.getType();
            code.append(outputType(type));
        }
        code.append(")");
        code.append(outputType(method.getReturnType()));
        return code.toString();
    }

    private String outputType(Type type) {
        if (type.getTypeOfElement().name().equals("ARRAYREF"))
            return "[" + outputType(((ArrayType) type).getElementType());
        else if (type.getTypeOfElement().name().equals("OBJECTREF"))
            return "L" + ((ClassType) type).getName() + ";";
        else
            return JasminConverter.typeToDescriptor.get(type.getTypeOfElement().name());
    }

    private String addToOperandStack(int value) {
        if (value < 0)
            return "ldc " + value + "\n";
        if (value <= 5)
            return "iconst_" + value + "\n";
        if (value <= 127)
            return "bipush " + value + "\n";
        if (value <= 32767)
            return "sipush " + value + "\n";
        return "ldc " + value + "\n";
    }

    private String addToOperandStack(String value) {
        return "ldc " + value + "\n";
    }

    private String checkImport(String importName, List<String> imports) {
        for (String fullImport : imports) {
            if (fullImport.contains(importName))
                return fullImport;
        }
        return "";
    }

    private String getMethodOrigin(CallInstruction instruction, List<String> methods, List<String> imports, String parentClass) {
        String methodName = ((LiteralElement) instruction.getSecondArg()).getLiteral().replace("\"", "");
        if (instruction.getInvocationType().name().contains("static")) {
            String fullImport = checkImport(((Operand) instruction.getFirstArg()).getName(), imports);
            if (fullImport.equals(""))
                return ((Operand) instruction.getFirstArg()).getName();
            return fullImport;
        }
        if (methods.contains(methodName)) {
            return ((ClassType) instruction.getFirstArg().getType()).getName();
        } else if ((instruction.getFirstArg().toString().equals("OBJECTREF")) && !checkImport(((ClassType) instruction.getFirstArg().getType()).getName(), imports).equals("")) {
            return checkImport(((ClassType) instruction.getFirstArg().getType()).getName(), imports);
        } else {
            return parentClass;
        }
    }

    public String getNextLabel() {
        this.label++;
        return "jasminLabel_" + this.label;
    }

    @Override
    public JasminResult toJasmin(OllirResult ollirResult) {
        StringBuilder jasminCode = new StringBuilder();
        ClassUnit ollirClassUnit = ollirResult.getOllirClass();

        if (ollirClassUnit.getSuperClass() == null) {
            ollirClassUnit.setSuperClass("java/lang/Object");
        }

        List<String> methods = new ArrayList<>();
        for (Method m : ollirClassUnit.getMethods()) {
            methods.add(m.getMethodName());
        }
        List<String> imports = new ArrayList<>();
        for (String importString : ollirClassUnit.getImports()) {
            imports.add(importString.replace('.', '/'));
        }

        jasminCode.append(".class ").append("public").append(" ").append(ollirClassUnit.getClassName()).append("\n");
        jasminCode.append(".super ").append(ollirClassUnit.getSuperClass()).append("\n\n\n");
        for (Field field : ollirClassUnit.getFields()) {
            jasminCode.append(processField(field));
        }

        ArrayList<Method> methodsObject = ollirClassUnit.getMethods();
        methodsObject.sort((m1, m2) -> {
            boolean m1IsConstructor = m1.isConstructMethod();
            boolean m2IsConstructor = m2.isConstructMethod();
            if (m1IsConstructor && !m2IsConstructor) {
                return -1;
            } else if (!m1IsConstructor && m2IsConstructor) {
                return 1;
            } else {
                return 0;
            }
        });
        HashMap<String, Method> methodMap = new HashMap<>();
        for (Method method : methodsObject) {
            methodMap.put(method.getMethodName(), method);
        }
        for (Method method : methodsObject) {
            List<Instruction> instructions = method.getInstructions();
            String staticStr = " static ", finalStr = " final ";
            if (!method.isStaticMethod()) {
                staticStr = " ";
            }
            if (!method.isFinalMethod()) {
                finalStr = "";
            }
            if (method.isConstructMethod()) {
                jasminCode.append(".method public ");
                method.addInstr(new ReturnInstruction());
            } else {
                jasminCode.append(".method ").append(method.getMethodAccessModifier().toString().equalsIgnoreCase("default") ? "private" : method.getMethodAccessModifier().toString().toLowerCase()).append(staticStr).append(finalStr);
            }
            jasminCode.append(outputMethodId(method));
            jasminCode.append("\n");
            StringBuilder limits = new StringBuilder();
            /*
            if (method.isStaticMethod())
                limits.append(".limit locals ").append(method.getVarTable().size()).append("\n");
            else {
                if (method.getVarTable().containsKey("this"))
                    limits.append(".limit locals ").append(method.getVarTable().size()).append("\n");
                else
                    limits.append(".limit locals ").append(method.getVarTable().size() + 1).append("\n");
            }*/
            limits.append(".limit locals 99\n");
            StringBuilder methodBody = new StringBuilder();
            for (Instruction instruction : instructions) {
                for (Map.Entry<String, Instruction> entry : method.getLabels().entrySet()) {
                    String key = entry.getKey();
                    Instruction value = entry.getValue();
                    if (instruction.equals(value)) {
                        methodBody.append(key).append(":\n");
                        break;
                    }
                }
                methodBody.append(this.dispatcher(instruction, method.getVarTable(), methods, imports, ollirClassUnit.getSuperClass()));
            }
            if (method.isConstructMethod() && method.getParams().isEmpty())
                methods.add("<init>");

            //jasminCode.append(".limit stack ").append(computeStackLimit(methodBody.toString().replaceAll("\\b\\w+\\s*:", ""), methodMap)).append("\n");
            limits.append(".limit stack 99\n");
            jasminCode.append(limits);
            jasminCode.append(methodBody);
            jasminCode.append(".end method").append("\n\n");
        }
        return new JasminResult(jasminCode.toString());
    }

    private String processField(Field field) {
        StringBuilder code = new StringBuilder();
        String staticStr = " static ", finalStr = " final ";
        if (!field.isStaticField()) {
            staticStr = "";
        }
        if (!field.isFinalField()) {
            finalStr = "";
        }
        code.append(".field ").append(field.getFieldAccessModifier().toString().equals("DEFAULT") ? "private" : field.getFieldAccessModifier().toString().toLowerCase()).append(" ").append(staticStr).append(finalStr).append(field.getFieldName()).append(" ").append(outputType(field.getFieldType()));
        return code.append("\n").toString();
    }

    private String processCall(CallInstruction instruction, HashMap<String, Descriptor> varTable, List<String> methods, List<String> imports, String parentClass) {
        StringBuilder code = new StringBuilder();
        String pop = instruction.getReturnType().getTypeOfElement().name().equals("VOID") ? "" : "pop\n";
        if (instruction.getInvocationType().name().equals("NEW")) {
            for (Element arg : instruction.getListOfOperands()) {
                code.append(handleLiteral(arg, varTable));
            }
            if (!((Operand) instruction.getFirstArg()).getType().getTypeOfElement().name().equals("ARRAYREF"))
                return code.append("new ").append(((Operand) instruction.getFirstArg()).getName()).append("\n").append(pop).toString();
            else {
                String type = ((ArrayType) instruction.getReturnType()).getElementType().getTypeOfElement().name().toLowerCase();
                type = type.substring(0, type.indexOf("32"));
                return code.append("newarray ").append(type).append("\n").append(pop).toString();
            }
        }
        if (instruction.getInvocationType().toString().equals("arraylength"))
            return code.append(handleLiteral(instruction.getFirstArg(), varTable)).append(instruction.getInvocationType().toString()).append("\n").append(pop).toString();
        if (!instruction.getInvocationType().name().contains("static"))
            code.append(handleLiteral(instruction.getFirstArg(), varTable));

        if (!instruction.getFirstArg().isLiteral()) {
            for (Element arg : instruction.getListOfOperands()) {
                code.append(handleLiteral(arg, varTable));
            }
        }

        boolean hasSecondArg = instruction.getSecondArg() != null;
        String secondArg = "", prefix = "";
        if (hasSecondArg) {
            secondArg = instruction.getSecondArg().toString();
            if (instruction.getSecondArg().isLiteral()) {
                secondArg = ((LiteralElement) instruction.getSecondArg()).getLiteral();
            }
            prefix = getMethodOrigin(instruction, methods, imports, parentClass) + "/";
        }
        return code.append(instruction.getInvocationType().name().toLowerCase()).append(" ").append(prefix).append(outputMethodId(secondArg, instruction.getListOfOperands(), instruction.getReturnType())).append("\n").append(pop).toString();
    }

    private String processGoTo(GotoInstruction instruction) {
        return "goto " + instruction.getLabel() + "\n";
    }

    private String processNoper(SingleOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        Element operand = instruction.getSingleOperand();
        return handleLiteral(operand, varTable);
    }

    private String processAssign(AssignInstruction instruction, HashMap<String, Descriptor> varTable, List<String> methods, List<String> imports, String parentClass) {
        StringBuilder code = new StringBuilder();
        this.dest = (Operand) instruction.getDest();
        String res = dispatcher(instruction.getRhs(), varTable, methods, imports, parentClass);
        if (instruction.getRhs() instanceof CallInstruction) {
            res = res.substring(0, res.lastIndexOf("pop\n"));
        }
        if (instruction.getDest() instanceof ArrayOperand arrayOperand) {
            Type type = new Type(ElementType.ARRAYREF);
            code.append(handleType(type, "load " + varTable.get(arrayOperand.getName()).getVirtualReg())).append("\n");
            code.append(handleLiteral(arrayOperand.getIndexOperands().get(0), varTable));
            code.append(res);
            code.append("iastore\n");
        } else {
            code.append(res);
            if (!res.contains("iinc")) {
                Operand tmpVariable = (Operand) instruction.getDest();
                code.append(handleType(varTable.get(tmpVariable.getName()).getVarType(), "store " + varTable.get(tmpVariable.getName()).getVirtualReg())).append("\n");
            }
        }
        return code.toString();
    }

    private String handleDifferentIfs(BinaryOpInstruction instruction, String label, HashMap<String, Descriptor> varTable) {
        String loaders = handleLiteral(instruction.getLeftOperand(), varTable) + handleLiteral(instruction.getRightOperand(), varTable);
        String res = switch (instruction.getOperation().getOpType().toString()) {
            case "LTH" -> "isub\n" + "iflt";
            case "GTH" -> "isub\n" + "ifgt";
            case "EQ" -> "isub\n" + "ifeq";
            case "NEQ" -> "isub\n" + "ifne";
            case "LTE" -> "isub\n" + "ifle";
            case "GTE" -> "isub\n" + "ifge";
            case "ANDB", "AND" -> "iand\n" + "ifne";
            case "ORB", "OR" -> "ior\n" + "ifne";
            default -> "IF ERROR";
        };
        return loaders + res + " " + label + "\n";
    }

    private String handleDifferentIfs(SingleOpInstruction singleOpInstruction, String label, HashMap<String, Descriptor> varTable) {
        StringBuilder code = new StringBuilder();
        code.append(handleLiteral(singleOpInstruction.getSingleOperand(), varTable));
        code.append("ifne ").append(label).append("\n");
        return code.toString();
    }

    private String handleDifferentIfs(UnaryOpInstruction singleOpInstruction, String label, HashMap<String, Descriptor> varTable) {
        StringBuilder code = new StringBuilder();
        code.append(handleLiteral(singleOpInstruction.getOperand(), varTable));
        code.append("ifeq ").append(label).append("\n");
        return code.toString();
    }

    private String processBranch(CondBranchInstruction instruction, HashMap<String, Descriptor> varTable) {
        if (instruction.getCondition() instanceof BinaryOpInstruction op)
            return handleDifferentIfs(op, instruction.getLabel(), varTable);
        else if (instruction.getCondition() instanceof SingleOpInstruction singleOpInstruction)
            return handleDifferentIfs(singleOpInstruction, instruction.getLabel(), varTable);
        else if (instruction.getCondition() instanceof UnaryOpInstruction unaryOpInstruction) {
            return handleDifferentIfs(unaryOpInstruction, instruction.getLabel(), varTable);
        } else
            return "PROCESS BRANCH\n";
    }

    private String processReturn(ReturnInstruction instruction, HashMap<String, Descriptor> varTable) {
        Element returnVar = instruction.getOperand();
        if (returnVar != null) {
            if (returnVar.isLiteral()) {
                return handleLiteral(returnVar, varTable) + "\n" + handleType(returnVar.getType(), "return\n");
            } else {
                Operand tmp = (Operand) returnVar;
                if (tmp.getName().equals("this"))
                    return "aload_0" + "\n" + handleType(returnVar.getType(), "return\n");
                return handleType(varTable.get(tmp.getName()).getVarType(), "load " + varTable.get(tmp.getName()).getVirtualReg()) + "\n" + handleType(returnVar.getType(), "return\n");
            }
        }
        return "return\n";
    }

    private String processGetField(GetFieldInstruction instruction, HashMap<String, Descriptor> varTable) {
        return handleType(varTable.get(((Operand) instruction.getFirstOperand()).getName()).getVarType(), "load_" + varTable.get(((Operand) instruction.getFirstOperand()).getName()).getVirtualReg()) + "\n" +
                "getfield " + ((ClassType) instruction.getFirstOperand().getType()).getName() + "/" + ((Operand) instruction.getSecondOperand()).getName() + " " + outputType(instruction.getSecondOperand().getType()) + "\n";
    }

    private String processPutField(PutFieldInstruction instruction, HashMap<String, Descriptor> varTable) {
        return handleType(varTable.get(((Operand) instruction.getFirstOperand()).getName()).getVarType(), "load_" + varTable.get(((Operand) instruction.getFirstOperand()).getName()).getVirtualReg()) + "\n" +
                handleLiteral(instruction.getThirdOperand(), varTable) +
                "putfield " + ((ClassType) instruction.getFirstOperand().getType()).getName() + "/" + ((Operand) instruction.getSecondOperand()).getName() + " " + outputType(instruction.getThirdOperand().getType()) + "\n";
    }

    private String processUnaryOp(UnaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        String operation = instruction.getOperation().getOpType().name(), code = "";
        if (operation.equals("NOT") || operation.equals("NOTB")) {
            code += "iconst_1\n";
            code += "ixor";
            return handleLiteral(instruction.getOperand(), varTable) + code + "\n";
        }
        return handleLiteral(instruction.getOperand(), varTable) + instruction.getOperation().getOpType().name().toLowerCase() + "\n";
    }

    private String handleLiteral(Element element, HashMap<String, Descriptor> varTable) {
        if (element.isLiteral()) {
            LiteralElement tmp = ((LiteralElement) element);

            if (element.getType().getTypeOfElement().name().equals("INT32") || element.getType().getTypeOfElement().name().equals("BOOLEAN"))
                return addToOperandStack(Integer.parseInt(tmp.getLiteral()));

            if (element.getType().getTypeOfElement().name().equals("STRING"))
                return addToOperandStack(tmp.getLiteral());

            return "ERROR HANDLE LITERAL\n";
        }

        if (element instanceof Operand operand && element.getType().getTypeOfElement().name().equals("BOOLEAN") && (operand.getName().equals("true") || operand.getName().equals("false"))) {
            return addToOperandStack(operand.getName().equals("true") ? 1 : 0);
        }

        if (element instanceof ArrayOperand tmp) {
            String res = handleType(varTable.get(tmp.getName()).getVarType(), "load " + varTable.get(tmp.getName()).getVirtualReg()) + "\n";
            res += handleLiteral(tmp.getIndexOperands().get(0), varTable) + "\n";
            res += handleType(tmp.getType(), "aload") + "\n";
            return res;
        }

        return (handleType(varTable.get(((Operand) element).getName()).getVarType(), "load " + varTable.get(((Operand) element).getName()).getVirtualReg())) + "\n";
    }

    private String processBinaryOp(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder code = new StringBuilder();
        String operation = instruction.getOperation().getOpType().toString().toLowerCase();
        Element leftOperand = instruction.getLeftOperand(), rightOperand = instruction.getRightOperand();
        // i = i + 1:
        if (this.dest.equals(leftOperand) && (operation.equals("add") || operation.equals("sub")) && !leftOperand.isLiteral() && rightOperand.isLiteral() && rightOperand.getType().getTypeOfElement() == ElementType.INT32) {
            int amount = Integer.parseInt(((LiteralElement) rightOperand).getLiteral()) * (operation.equals("sub") ? -1 : 1);
            if (amount >= -128 && amount <= 127 && varTable.get(((Operand) leftOperand).getName()) != null) {
                code.append("iinc ").append(varTable.get(((Operand) leftOperand).getName()).getVirtualReg()).append(" ").append(((LiteralElement) rightOperand).getLiteral()).append("\n");
                return code.toString();
            }
        }
        // i = 1 + i;
        if (this.dest.equals(rightOperand) && (operation.equals("add") || operation.equals("sub")) && !rightOperand.isLiteral() && leftOperand.isLiteral() && leftOperand.getType().getTypeOfElement() == ElementType.INT32) {
            int amount = Integer.parseInt(((LiteralElement) leftOperand).getLiteral()) * (operation.equals("sub") ? -1 : 1);
            if (amount >= -128 && amount <= 127 && varTable.get(((Operand) rightOperand).getName()) != null) {
                code.append("iinc ").append(varTable.get(((Operand) rightOperand).getName()).getVirtualReg()).append(" ").append(((LiteralElement) leftOperand).getLiteral()).append("\n");
                return code.toString();
            }
        }
        code.append(handleLiteral(leftOperand, varTable));
        code.append(handleLiteral(rightOperand, varTable));
        if (operation.equals("add") || operation.equals("mul") || operation.equals("div") || operation.equals("sub"))
            code.append("i");
        if (operation.equals("andb") || operation.equals("orb")) {
            code.append("i");
            operation = operation.substring(0, operation.length() - 1);
        }
        // a < b: a-b < 0
        if (operation.equals("lth") || operation.equals("gth") || operation.equals("lte") || operation.equals("gte") || operation.equals("eq") || operation.equals("neq")) {
            String ifType = "";
            switch (operation) {
                case "lth" -> ifType = "iflt";
                case "gth" -> ifType = "ifgt";
                case "lte" -> ifType = "ifle";
                case "gte" -> ifType = "ifge";
                case "eq" -> ifType = "ifeq";
                case "neq" -> ifType = "ifne";
            }
            String trueLabel = getNextLabel(), doneLabel = getNextLabel();
            code.append("isub\n").append(ifType).append(" ").append(trueLabel).append("\n").append(addToOperandStack(0)).append("goto ").append(doneLabel).append("\n").append(trueLabel).append(":\n").append(addToOperandStack(1)).append(doneLabel).append(":\n");
        } else {
            code.append(operation).append("\n");
        }
        return code.toString();
    }
}
