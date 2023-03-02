package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SymbolTable implements pt.up.fe.comp.jmm.analysis.table.SymbolTable {

    Map<String, ArrayList<Symbol>> table;

    @Override
    public List<String> getImports() {
        ArrayList<Symbol> symbols = table.get("import");
        List<String> imports = new ArrayList<String>();
        for (Symbol symbol : symbols) {
            imports.add(symbol.getName());
        }

        return imports;
    }

    @Override
    public String getClassName() {
        return table.get("class").get(0).getName();
    }

    @Override
    public String getSuper() {
        return table.get("extends").get(0).getName();
    }

    @Override
    public List<Symbol> getFields() {
        return table.get("fields");
    }

    @Override
    public List<String> getMethods() {
        ArrayList<Symbol> symbols = table.get("method");
        List<String> methods = new ArrayList<String>();
        for (Symbol symbol : symbols) {
            methods.add(symbol.getName());
        }

        return methods;
    }

    @Override
    public Type getReturnType(String s) {
        //assumindo que o type de um Symbol method é o tipo de retorno dessa funçao
        List<String> methods = getMethods();
        int index = methods.indexOf(s);
        return table.get("method").get(index).getType();
    }

    @Override
    public List<Symbol> getParameters(String s) {
        //assumindo que existe uma entrada no map com key igual ao nome do method e value igual a uma lista com local variables e parametros
        return table.get(s+"_params");
    }

    @Override
    public List<Symbol> getLocalVariables(String s) {
        return table.get(s+ "_variables");
    }
}
