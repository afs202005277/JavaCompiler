package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.parser.JmmParserResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SymbolTable implements pt.up.fe.comp.jmm.analysis.table.SymbolTable, JmmAnalysis {

    Map<String, ArrayList<Symbol>> table;

    public SymbolTable() {
        this.table = new HashMap<>();
    }

    public void addEntry(String key, ArrayList<Symbol> list) {
        if (table.containsKey(key)) {
            ArrayList<Symbol> value = table.get(key);
            value.addAll(list);
            table.put(key, value);
        } else {
            table.put(key, list);
        }
    }

    public ArrayList<Symbol> getSomethingFromTable(String something) {
        return table.getOrDefault(something, null);
    }

    @Override
    public List<String> getImports() {
        ArrayList<Symbol> symbols = table.get("import");
        List<String> imports = new ArrayList<String>();
        if (symbols != null) {
            for (Symbol symbol : symbols) {
                imports.add(symbol.getName());
            }
        }

        return imports;
    }

    @Override
    public String getClassName() {
        return table.get("class").get(0).getName();
    }

    @Override
    public String getSuper() {
        if (table.get("extends") != null)
            return table.get("extends").get(0).getName();
        else
            return "";
    }

    @Override
    public List<Symbol> getFields() {
        List<Symbol> fields = table.get("fields");
        return fields == null ? new ArrayList<>() : fields;
    }

    @Override
    public List<String> getMethods() {
        ArrayList<Symbol> symbols = table.get("methods");
        List<String> methods = new ArrayList<String>();
        if (symbols != null) {
            for (Symbol symbol : symbols) {
                methods.add(symbol.getName());
            }
        }

        return methods;
    }

    @Override
    public Type getReturnType(String s) {
        List<String> methods = getMethods();
        int index = methods.indexOf(s);
        return table.get("methods").get(index).getType();
    }

    @Override
    public List<Symbol> getParameters(String s) {
        List<Symbol> params = table.get(s + "_params");
        return params == null ? new ArrayList<>() : params;
    }

    @Override
    public List<Symbol> getLocalVariables(String s) {
        List<Symbol> locals = table.get(s + "_variables");
        return locals == null ? new ArrayList<>() : locals;
    }

    public static String typeToString(Type type) {
        return ", VarType: " + type.getName() + ", IsArray: " + type.isArray();
    }

    public static String symbolToString(Symbol symbol) {
        return "VarName: " + symbol.getName() + SymbolTable.typeToString(symbol.getType());
    }

    public static String listSymbolsString(List<Symbol> symbols) {
        if (symbols.size() == 0)
            return "";
        StringBuilder s_symbols = new StringBuilder("[");
        for (int i = 0; i < symbols.size() - 1; i++) {
            s_symbols.append("[").append(SymbolTable.symbolToString(symbols.get(i))).append("], ");
        }
        s_symbols.append("[").append(SymbolTable.symbolToString(symbols.get(symbols.size() - 1))).append("]]");
        return s_symbols.toString();
    }

    public String print() {
        List<String> methods = this.getMethods();
        System.out.println("Classes imported: " + this.getImports().toString());
        System.out.println("Parent class: " + this.getSuper());
        System.out.println("Class name: " + this.getClassName());
        System.out.println("Class fields: " + listSymbolsString(this.getFields()));
        System.out.println("Methods details:\n");

        for (String method : methods) {
            System.out.println("Method: " + method);
            System.out.println("Parameters: " + listSymbolsString(this.getParameters(method)));
            System.out.println("Local variables: " + listSymbolsString(this.getLocalVariables(method)));
            System.out.println("Return type: " + this.getReturnType(method) + "\n");
        }
        return null;
    }

    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult jmmParserResult) {
        ASTConverter gen = new ASTConverter();
        gen.visit(jmmParserResult.getRootNode(), "");

        SymbolTable symbolTable = gen.getTable();
        return new JmmSemanticsResult(jmmParserResult, symbolTable, jmmParserResult.getReports());
    }
}
