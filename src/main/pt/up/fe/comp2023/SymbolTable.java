package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SymbolTable implements pt.up.fe.comp.jmm.analysis.table.SymbolTable {

    Map<String, ArrayList<Symbol>> table;

    public SymbolTable() {
        this.table = new HashMap<>();
    }

    public void addEntry(String key, ArrayList<Symbol> list){
        if (table.containsKey(key)){
            ArrayList<Symbol> value = table.get(key);
            value.addAll(list);
            table.put(key, value);
        } else{
            table.put(key, list);
        }
    }

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
        ArrayList<Symbol> symbols = table.get("methods");
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
        return table.get("methods").get(index).getType();
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

    public static String typeToString(Type type){
        return ", VarType: " + type.getName() + ", IsArray: " + type.isArray();
    }

    public static String symbolToString(Symbol symbol){
        return "VarName: " + symbol.getName() + SymbolTable.typeToString(symbol.getType());
    }

    public static String listSymbolsString(List<Symbol> symbols){
        StringBuilder s_symbols = new StringBuilder("[");
        for (int i=0;i<symbols.size()-1;i++){

            s_symbols.append("[").append(SymbolTable.symbolToString(symbols.get(i))).append("], ");
        }
        s_symbols.append("[").append(SymbolTable.symbolToString(symbols.get(symbols.size()-1))).append("]]");
        return s_symbols.toString();
    }

    public void printTable(){
        List<String> methods = this.getMethods();
        System.out.println("Classes imported: " + this.getImports().toString());
        System.out.println("Parent class: " + this.getSuper());
        System.out.println("Class name: " + this.getClassName());
        System.out.println("Class fields: " + listSymbolsString(this.getFields()));
        System.out.println("Methods details:\n");

        for (String method:methods) {
            System.out.println("Method: " +  method);
            System.out.println("Parameters: " + listSymbolsString(this.getParameters(method)));
            System.out.println("Local variables: " + listSymbolsString(this.getLocalVariables(method)));
            System.out.println("Return type: " + this.getReturnType(method) + "\n");
        }
    }
}
