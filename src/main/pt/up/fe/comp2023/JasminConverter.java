package pt.up.fe.comp2023;

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

    private String handleType(String suffix, Descriptor variable) {
        StringBuilder jasminCode = new StringBuilder();
        // INT32, BOOLEAN, ARRAYREF, OBJECTREF, THIS,  STRING, VOID
        String variableType = variable.getVarType().getTypeOfElement().name();
        switch (variableType) {
            case "THIS" -> jasminCode.append("a").append(suffix);
            case "INT32" -> jasminCode.append("l").append(suffix);
            case "BOOLEAN" -> jasminCode.append("a").append(suffix);
            case "ARRAYREF" -> jasminCode.append("a").append(suffix);
            case "OBJECTREF" -> jasminCode.append("a").append(suffix);
            case "STRING" -> jasminCode.append("a").append(suffix);
            case "VOID" -> jasminCode.append(suffix);
        }
        return jasminCode.toString();
    }

    private String handleType(Type type) {
        StringBuilder jasminCode = new StringBuilder();
        // INT32, BOOLEAN, ARRAYREF, OBJECTREF, THIS,  STRING, VOID
        switch (type.getTypeOfElement().name()) {
            case "THIS" -> jasminCode.append("a").append("return");
            case "INT32" -> jasminCode.append("l").append("return");
            case "BOOLEAN" -> jasminCode.append("a").append("return");
            case "ARRAYREF" -> jasminCode.append("a").append("return");
            case "OBJECTREF" -> jasminCode.append("a").append("return");
            case "STRING" -> jasminCode.append("a").append("return");
            case "VOID" -> jasminCode.append("return");
        }
        return jasminCode.toString();
    }

    private String dispatcher(Instruction instruction, HashMap<String, Descriptor> varTable){
        StringBuilder jasminCode = new StringBuilder();
        switch (instruction.getInstType()) {
            case CALL -> jasminCode.append(processCall((CallInstruction) instruction, varTable));
            case GOTO -> jasminCode.append(processGoTo((GotoInstruction) instruction));
            case NOPER -> jasminCode.append(processNoper(instruction));
            case ASSIGN -> jasminCode.append(processAssign((AssignInstruction) instruction, varTable));
            case BRANCH -> jasminCode.append(processBranch(instruction));
            case RETURN -> jasminCode.append(processReturn((ReturnInstruction) instruction));
            case GETFIELD -> jasminCode.append(processGetField((FieldInstruction) instruction));
            case PUTFIELD -> jasminCode.append(processPutField((FieldInstruction) instruction));
            case UNARYOPER -> jasminCode.append(processUnaryOp((UnaryOpInstruction) instruction));
            case BINARYOPER -> jasminCode.append(processBinaryOp((BinaryOpInstruction) instruction));
        }
        return jasminCode.toString();
    }

    @Override
    public JasminResult toJasmin(OllirResult ollirResult) {
        StringBuilder jasminCode = new StringBuilder();
        ClassUnit ollirClassUnit = ollirResult.getOllirClass();

        jasminCode.append(".class ").append(ollirClassUnit.getClassAccessModifier()).append(" ").append(ollirClassUnit.getClassName()).append("\n");

        jasminCode.append(".super ").append(ollirClassUnit.getSuperClass()).append("\n\n\n");

        for (Method method : ollirClassUnit.getMethods()) {
            List<Instruction> instructions = method.getInstructions();
            String staticStr = " static ";
            if (!method.isStaticMethod()) {
                staticStr = " ";
            }
            if (ollirClassUnit.getClassName().equals(method.getMethodName())) {
                jasminCode.append(".method public <init>");
                method.addInstr(new ReturnInstruction());
            } else {
                jasminCode.append(".method ").append(method.getMethodAccessModifier()).append(staticStr).append(method.getMethodName());
            }
            jasminCode.append("(");
            for (Element element : method.getParams()) {
                Type type = element.getType();
                if (type.getTypeOfElement().name().equals("ARRAYREF")) {
                    ArrayType tmp = (ArrayType) type;
                    jasminCode.append("[").append(typeToDescriptor.get(tmp.getElementClass()));
                } else {
                    jasminCode.append(typeToDescriptor.get(element.getType().getTypeOfElement().name()));
                }
            }
            jasminCode.append(")");
            jasminCode.append(JasminConverter.typeToDescriptor.get(method.getReturnType().getTypeOfElement().name()));
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
        if (instruction.getInvocationType().name().equals("NEW")){
            return code.append("new ").append(((Operand) instruction.getFirstArg()).getName()).append("\n").toString();
        }
        String secondArg = instruction.getSecondArg().toString();
        if (!(instruction.getFirstArg().toString().equals("CLASS") || instruction.getFirstArg().toString().equals("VOID"))) {
            Operand operand = (Operand) instruction.getFirstArg();

            code.append(handleType("load_" + varTable.get(operand.getName()).getVirtualReg(), varTable.get(operand.getName()))).append("\n");
        }
        if (instruction.getSecondArg().isLiteral()) {
            secondArg = ((LiteralElement) instruction.getSecondArg()).getLiteral();
        }
        return code.append(instruction.getInvocationType().name()).append(" ").append(secondArg.replace("\"", "")).append("()V").append("\n").toString();
    }

    private String processGoTo(GotoInstruction instruction) {
        return null;
    }

    private String processNoper(Instruction instruction) {
        return null;
    }

    private String processAssign(AssignInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder code = new StringBuilder();
        code.append(dispatcher(instruction.getRhs(), varTable));
        return code.toString();
    }

    private String processBranch(Instruction instruction) {
        return null;
    }

    private String processReturn(ReturnInstruction instruction) {
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

    private String processBinaryOp(BinaryOpInstruction instruction) {
        return null;
    }
}
