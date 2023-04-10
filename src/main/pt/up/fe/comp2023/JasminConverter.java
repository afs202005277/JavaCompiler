package pt.up.fe.comp2023;
// terminar o NOPER para os varios tipos de element
// tratar de meter as labels
// fazer o unary op


// ele n funciona se o access modifier da class for DEFAULT

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class JasminConverter implements pt.up.fe.comp.jmm.jasmin.JasminBackend {
    private static final HashMap<String, String> typeToDescriptor = new HashMap<>() {{
        put("BOOLEAN", "Z");
        put("INT32", "I");
        put("STRING", "Ljava/lang/String;");
        put("VOID", "V");
    }};

    private String handleType(Type type, String suffix) {
        StringBuilder jasminCode = new StringBuilder();
        // INT32, BOOLEAN, ARRAYREF, OBJECTREF, THIS,  STRING, VOID
        switch (type.getTypeOfElement().name()) {
            case "THIS" -> jasminCode.append("a").append(suffix);
            case "INT32" -> jasminCode.append("i").append(suffix);
            case "BOOLEAN" -> jasminCode.append("i").append(suffix);
            case "ARRAYREF" -> jasminCode.append("a").append(suffix);
            case "OBJECTREF" -> jasminCode.append("a").append(suffix);
            case "STRING" -> jasminCode.append("a").append(suffix);
            case "VOID" -> jasminCode.append(suffix);
        }
        return jasminCode.toString();
    }

    private String dispatcher(Instruction instruction, HashMap<String, Descriptor> varTable, List<String> methods, List<String> imports, String parentClass) {
        StringBuilder jasminCode = new StringBuilder();
        switch (instruction.getInstType()) {
            case CALL -> jasminCode.append(processCall((CallInstruction) instruction, varTable, methods, imports, parentClass));
            case GOTO -> jasminCode.append(processGoTo((GotoInstruction) instruction));
            case NOPER -> jasminCode.append(processNoper((SingleOpInstruction) instruction));
            case ASSIGN ->
                    jasminCode.append(processAssign((AssignInstruction) instruction, varTable, methods, imports, parentClass));
            case BRANCH -> jasminCode.append(processBranch((CondBranchInstruction) instruction, varTable, methods, imports, parentClass));
            case RETURN -> jasminCode.append(processReturn((ReturnInstruction) instruction, varTable));
            case GETFIELD -> jasminCode.append(processGetField((GetFieldInstruction) instruction, varTable));
            case PUTFIELD -> jasminCode.append(processPutField((PutFieldInstruction) instruction, varTable));
            case UNARYOPER -> jasminCode.append(processUnaryOp((UnaryOpInstruction) instruction));
            case BINARYOPER -> jasminCode.append(processBinaryOp((BinaryOpInstruction) instruction, varTable));
            default -> jasminCode.append("UNKNOWN INSTRUCTION");
        }
        return jasminCode.toString();
    }

    private String outputMethodId(String methodName, List<Element> args, Type returnType) {
        Method method = new Method(new ClassUnit());
        method.setMethodName(methodName.replace("\"", ""));
        if (args != null){
            for (Element arg : args) {
                method.addParam(arg);
            }
        }
        method.setReturnType(returnType);
        return outputMethodId(method, false);
    }

    private String outputMethodId(Method method, boolean isInit) {
        StringBuilder code = new StringBuilder();
        if (isInit) {
            code.append("<init>");
        } else {
            code.append(method.getMethodName());
        }
        code.append("(");
        for (Element element : method.getParams()) {
            Type type = element.getType();
            if (type.getTypeOfElement().name().equals("ARRAYREF")) {
                ArrayType tmp = (ArrayType) type;
                code.append("[").append(typeToDescriptor.get(tmp.getElementType().toString()));
            } else {
                code.append(typeToDescriptor.get(element.getType().getTypeOfElement().name()));
            }
        }
        code.append(")");
        code.append(JasminConverter.typeToDescriptor.get(method.getReturnType().getTypeOfElement().name()));
        return code.toString();
    }

    private String addToOperandStack(int value) {
        if (value >= -1 && value <= 5)
            return "iconst_" + value + "\n";
        if (value >= -128 && value <= 127)
            return "bipush " + value + "\n";
        if (value >= -32768 && value <= 32767)
            return "sipush " + value + "\n";
        return "ldc " + value + "\n";
    }

    private String getMethodOrigin(CallInstruction instruction, List<String> methods, List<String> imports, String parentClass){
        String methodName = ((LiteralElement) instruction.getSecondArg()).getLiteral().replace("\"", "");
        if (methods.contains(methodName)){
            return ((ClassType) instruction.getFirstArg().getType()).getName();
        } else if(imports.contains(((Operand) instruction.getFirstArg()).getName())){
            return ((Operand) instruction.getFirstArg()).getName();
        } else{
            return parentClass;
        }
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
        List<String> imports = new ArrayList<>(ollirClassUnit.getImports());

        // jasminCode.append(".class ").append(ollirClassUnit.getClassAccessModifier().toString().toLowerCase()).append(" ").append(ollirClassUnit.getClassName()).append("\n");
        jasminCode.append(".class ").append("public").append(" ").append(ollirClassUnit.getClassName()).append("\n");
        jasminCode.append(".super ").append(ollirClassUnit.getSuperClass()).append("\n\n\n");

        for (Method method : ollirClassUnit.getMethods()) {
            List<Instruction> instructions = method.getInstructions();
            boolean isInit = method.getMethodName().equals(ollirClassUnit.getClassName());
            String staticStr = " static ", finalStr = " final ";
            if (!method.isStaticMethod()) {
                staticStr = " ";
            }
            if (!method.isFinalMethod()){
                finalStr = " ";
            }
            if (isInit) {
                jasminCode.append(".method public ");
                method.addInstr(new ReturnInstruction());
            } else {
                jasminCode.append(".method ").append(method.getMethodAccessModifier().toString().toLowerCase()).append(staticStr).append(finalStr);
            }
            jasminCode.append(outputMethodId(method, isInit));
            jasminCode.append("\n");
            if (!method.getVarTable().isEmpty()) {
                jasminCode.append(".limit stack 99\n");
                jasminCode.append(".limit locals 99\n");
            }
            for (Instruction instruction : instructions) {
                jasminCode.append(this.dispatcher(instruction, method.getVarTable(), methods, imports, ollirClassUnit.getSuperClass()));
            }
            if (isInit)
                methods.add("<init>");
            jasminCode.append(".end method").append("\n\n");
        }
        return new JasminResult(jasminCode.toString());
    }

    private String processCall(CallInstruction instruction, HashMap<String, Descriptor> varTable, List<String> methods, List<String> imports, String parentClass) {
        StringBuilder code = new StringBuilder();
        if (instruction.getInvocationType().name().equals("NEW")) {
            return code.append("new ").append(((Operand) instruction.getFirstArg()).getName()).append("\n").toString();
        }
        boolean hasSecondArg = instruction.getSecondArg() != null;
        if (!(instruction.getFirstArg().toString().equals("CLASS") || instruction.getFirstArg().toString().equals("VOID") || instruction.getFirstArg().toString().equals("ARRAYREF"))) {
            Operand operand = (Operand) instruction.getFirstArg();

            code.append(handleType(varTable.get(operand.getName()).getVarType(), "load " + varTable.get(operand.getName()).getVirtualReg())).append("\n");

            for (Element arg : instruction.getListOfOperands()) {
                Operand tmp = (Operand) arg;
                code.append(handleType(varTable.get(tmp.getName()).getVarType(), "load " + varTable.get(tmp.getName()).getVirtualReg())).append("\n");
            }
        }
        String secondArg = "", prefix = "";
        if (hasSecondArg){
            secondArg = instruction.getSecondArg().toString();
            if (instruction.getSecondArg().isLiteral()) {
                secondArg = ((LiteralElement) instruction.getSecondArg()).getLiteral();
            }
            prefix = getMethodOrigin(instruction, methods, imports, parentClass) + "/";
        }

        return code.append(instruction.getInvocationType().name()).append(" ").append(prefix).append(outputMethodId(secondArg, instruction.getListOfOperands(), instruction.getReturnType())).append("\n").toString();
    }

    private String processGoTo(GotoInstruction instruction) {
        return "goto " + instruction.getLabel() + "\n";
    }

    private String processNoper(SingleOpInstruction instruction) {
        Element operand = instruction.getSingleOperand();
        return handleLiteral(operand, varTable);
    }

    private String processAssign(AssignInstruction instruction, HashMap<String, Descriptor> varTable, List<String> methods, List<String> imports, String parentClass) {
        StringBuilder code = new StringBuilder();
        code.append(dispatcher(instruction.getRhs(), varTable, methods, imports, parentClass));
        Operand tmpVariable = ((Operand) instruction.getDest());
        code.append(handleType(varTable.get(tmpVariable.getName()).getVarType(), "store " + varTable.get(tmpVariable.getName()).getVirtualReg())).append("\n");
        return code.toString();
    }

    private String handleDifferentIfs(BinaryOpInstruction instruction, String label){
        String res = switch (instruction.getOperation().getOpType().toString()) {
            case "LTH" -> "if_icmplt";
            case "GTH" -> "if_icmpgt";
            case "EQ" -> "if_icmpeq";
            case "NEQ" -> "if_icmpne";
            case "LTE" -> "if_icmple";
            case "GTE" -> "if_icmpge";
            default -> "IF ERROR";
        };
        return res + " " + label + "\n";
    }
    private String processBranch(CondBranchInstruction instruction, HashMap<String, Descriptor> varTable, List<String> methods, List<String> imports, String parentClass) {
        return this.dispatcher(instruction.getCondition(), varTable, methods, imports, parentClass) + handleDifferentIfs((BinaryOpInstruction) instruction.getCondition(), instruction.getLabel());
    }

    private String processReturn(ReturnInstruction instruction, HashMap<String, Descriptor> varTable) {
        Element returnVar = instruction.getOperand();
        if (returnVar != null) {
            if (returnVar.isLiteral()) {
                return addToOperandStack(Integer.parseInt(((LiteralElement) returnVar).getLiteral())) + "\n" + "ireturn\n";
            } else {
                Operand tmp = (Operand) returnVar;
                return handleType(varTable.get(tmp.getName()).getVarType(), "load " + varTable.get(tmp.getName()).getVirtualReg()) + "\n" + handleType(returnVar.getType(), "return\n");
            }
        }
        return "return\n";
    }

    private String processGetField(GetFieldInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder code = new StringBuilder();
        code.append(handleType(varTable.get(((Operand) instruction.getFirstOperand()).getName()).getVarType(), "load_" + varTable.get(((Operand) instruction.getFirstOperand()).getName()).getVirtualReg())).append("\n");
        code.append("getfield ").append(((ClassType) instruction.getFirstOperand().getType()).getName()).append("/").append(((Operand) instruction.getSecondOperand()).getName()).append(" ").append(typeToDescriptor.get(instruction.getSecondOperand().getType().toString())).append("\n");
        return code.toString();
    }

    private String processPutField(PutFieldInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder code = new StringBuilder();
        code.append(handleType(varTable.get(((Operand) instruction.getFirstOperand()).getName()).getVarType(), "load_" + varTable.get(((Operand) instruction.getFirstOperand()).getName()).getVirtualReg())).append("\n");
        if (instruction.getThirdOperand().isLiteral()) {
            // TO DO: only works for ints
            code.append(addToOperandStack(Integer.parseInt(((LiteralElement) instruction.getThirdOperand()).getLiteral())));
        } else {
            code.append(handleType(instruction.getThirdOperand().getType(), "load_" + varTable.get(((Operand) instruction.getThirdOperand()).getName()).getVirtualReg()));
        }
        code.append("putfield ").append(((ClassType) instruction.getFirstOperand().getType()).getName()).append("/").append(((Operand) instruction.getSecondOperand()).getName()).append(" ").append(typeToDescriptor.get(instruction.getThirdOperand().getType().toString())).append("\n");
        return code.toString();
    }

    private String processUnaryOp(UnaryOpInstruction instruction) {
        return null;
    }

    private String handleLiteral(Element element, HashMap<String, Descriptor> varTable){
        if (element.isLiteral()){
            LiteralElement tmp = ((LiteralElement) element);
            return (addToOperandStack(Integer.parseInt(tmp.getLiteral())));
        } else{
            return (handleType(varTable.get(((Operand) element).getName()).getVarType(), "load " + varTable.get(((Operand) element).getName()).getVirtualReg())) + "\n";
        }
    }
    private String processBinaryOp(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder code = new StringBuilder();
        Element leftOperand = instruction.getLeftOperand(), rightOperand = instruction.getRightOperand();
        code.append(handleLiteral(leftOperand, varTable));
        code.append(handleLiteral(rightOperand, varTable));
        return code.toString();
    }
}
