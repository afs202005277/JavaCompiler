package pt.up.fe.comp2023;

public class Node {
    private final String register;
    private String color;

    public Node(String register) {
        this.register = register;
        this.color = "white";
    }

    public String getRegister() {
        return register;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}

