package pt.up.fe.comp2023;
// criar funcao para variar entre iload_n e iload n (e o msm para os stores)

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;

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
            case "BOOLEAN" -> jasminCode.append("a").append(suffix);
            case "ARRAYREF" -> jasminCode.append("a").append(suffix);
            case "OBJECTREF" -> jasminCode.append("a").append(suffix);
            case "STRING" -> jasminCode.append("a").append(suffix);
            case "VOID" -> jasminCode.append(suffix);
        }
        return jasminCode.toString();
    }

    private String dispatcher(Instruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder jasminCode = new StringBuilder();
        switch (instruction.getInstType()) {
            case CALL -> jasminCode.append(processCall((CallInstruction) instruction, varTable));
            case GOTO -> jasminCode.append(processGoTo((GotoInstruction) instruction));
            case NOPER -> jasminCode.append(processNoper((SingleOpInstruction) instruction));
            case ASSIGN -> jasminCode.append(processAssign((AssignInstruction) instruction, varTable));
            case BRANCH -> jasminCode.append(processBranch(instruction));
            case RETURN -> jasminCode.append(processReturn((ReturnInstruction) instruction, varTable));
            case GETFIELD -> jasminCode.append(processGetField((FieldInstruction) instruction));
            case PUTFIELD -> jasminCode.append(processPutField((FieldInstruction) instruction));
            case UNARYOPER -> jasminCode.append(processUnaryOp((UnaryOpInstruction) instruction));
            case BINARYOPER -> jasminCode.append(processBinaryOp((BinaryOpInstruction) instruction, varTable));
        }
        return jasminCode.toString();
    }

    private String outputMethodId(String methodName, List<Element> args, Type returnType) {
        Method method = new Method(new ClassUnit());
        method.setMethodName(methodName.replace("\"", ""));
        for (Element arg : args) {
            method.addParam(arg);
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

    @Override
    public JasminResult toJasmin(OllirResult ollirResult) {
        StringBuilder jasminCode = new StringBuilder();
        ClassUnit ollirClassUnit = ollirResult.getOllirClass();

        jasminCode.append(".class ").append(ollirClassUnit.getClassAccessModifier()).append(" ").append(ollirClassUnit.getClassName()).append("\n");

        jasminCode.append(".super ").append(ollirClassUnit.getSuperClass()).append("\n\n\n");

        for (Method method : ollirClassUnit.getMethods()) {
            List<Instruction> instructions = method.getInstructions();
            boolean isInit = method.getMethodName().equals(ollirClassUnit.getClassName());
            String staticStr = " static ";
            if (!method.isStaticMethod()) {
                staticStr = " ";
            }
            if (isInit) {
                jasminCode.append(".method public ");
                method.addInstr(new ReturnInstruction());
            } else {
                jasminCode.append(".method ").append(method.getMethodAccessModifier()).append(staticStr);
            }
            jasminCode.append(outputMethodId(method, isInit));
            jasminCode.append("\n");
            if (!method.getVarTable().isEmpty()) {
                jasminCode.append(".limit stack 99\n");
                jasminCode.append(".limit locals 99\n");
            }
            for (Instruction instruction : instructions) {
                jasminCode.append(this.dispatcher(instruction, method.getVarTable()));
            }
            jasminCode.append(".end method").append("\n\n");
        }
        return new JasminResult(jasminCode.toString());
    }

    private String processCall(CallInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder code = new StringBuilder();
        if (instruction.getInvocationType().name().equals("NEW")) {
            return code.append("new ").append(((Operand) instruction.getFirstArg()).getName()).append("\n").toString();
        }
        String secondArg = instruction.getSecondArg().toString();
        if (!(instruction.getFirstArg().toString().equals("CLASS") || instruction.getFirstArg().toString().equals("VOID"))) {
            Operand operand = (Operand) instruction.getFirstArg();

            code.append(handleType(varTable.get(operand.getName()).getVarType(), "load " + varTable.get(operand.getName()).getVirtualReg())).append("\n");

            for (Element arg : instruction.getListOfOperands()) {
                Operand tmp = (Operand) arg;
                code.append(handleType(varTable.get(tmp.getName()).getVarType(), "load " + varTable.get(tmp.getName()).getVirtualReg())).append("\n");
            }
        }
        if (instruction.getSecondArg().isLiteral()) {
            secondArg = ((LiteralElement) instruction.getSecondArg()).getLiteral();
        }
        return code.append(instruction.getInvocationType().name()).append(" ").append(((ClassType) ((Operand) instruction.getFirstArg()).getType()).getName()).append("/").append(outputMethodId(secondArg, instruction.getListOfOperands(), instruction.getReturnType())).append("\n").toString();
    }

    private String processGoTo(GotoInstruction instruction) {
        return null;
    }

    private String processNoper(SingleOpInstruction instruction) {
        if (instruction.getSingleOperand().isLiteral())
            return addToOperandStack(Integer.parseInt(((LiteralElement) instruction.getSingleOperand()).getLiteral()));
        return "";
    }

    private String processAssign(AssignInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder code = new StringBuilder();
        code.append(dispatcher(instruction.getRhs(), varTable));
        Operand tmpVariable = ((Operand) instruction.getDest());
        code.append(handleType(varTable.get(tmpVariable.getName()).getVarType(), "store " + varTable.get(tmpVariable.getName()).getVirtualReg())).append("\n");
        return code.toString();
    }

    private String processBranch(Instruction instruction) {
        return null;
    }

    private String processReturn(ReturnInstruction instruction, HashMap<String, Descriptor> varTable) {
        Operand returnVar = (Operand) instruction.getOperand();
        if (returnVar != null) {
            return handleType(varTable.get(returnVar.getName()).getVarType(), "load " + varTable.get(returnVar.getName()).getVirtualReg()) + "\n" + handleType(returnVar.getType(), "return\n");
        }
        return "return\n";
    }

    private String processGetField(FieldInstruction instruction) {
        return null;
    }

    private String processPutField(FieldInstruction instruction) {
        return null;
    }

    private String processUnaryOp(UnaryOpInstruction instruction) {
        return null;
    }

    private String processBinaryOp(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder code = new StringBuilder();
        code.append(handleType(varTable.get(((Operand) instruction.getLeftOperand()).getName()).getVarType(), "load " + varTable.get(((Operand) instruction.getLeftOperand()).getName()).getVirtualReg())).append("\n");
        code.append(handleType(varTable.get(((Operand) instruction.getRightOperand()).getName()).getVarType(), "load " + varTable.get(((Operand) instruction.getRightOperand()).getName()).getVirtualReg())).append("\n");
        code.append(handleType(instruction.getOperation().getTypeInfo(), instruction.getOperation().getOpType().name().toLowerCase())).append("\n");
        return code.toString();
    }
}
