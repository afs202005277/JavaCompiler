package pt.up.fe.comp2023;

public class InterferenceGraphNode {
    private final String register;
    private String color;

    public InterferenceGraphNode(String register) {
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        InterferenceGraphNode other = (InterferenceGraphNode) obj;
        return register.equals(other.register);
    }

    @Override
    public int hashCode() {
        return register.hashCode();
    }

}
