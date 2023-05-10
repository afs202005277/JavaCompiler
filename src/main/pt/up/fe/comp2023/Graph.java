package pt.up.fe.comp2023;

import java.util.*;

public class Graph {
    private int V;
    private final Map<Node, List<Node>> adjList;

    public Graph() {
        V = 0;
        adjList = new HashMap<>();
    }

    public void addVertex(Node node) {
        adjList.put(node, new LinkedList<>());
        V++;
    }

    public void addEdge(Node v, Node w) {
        adjList.get(v).add(w);
        adjList.get(w).add(v);
    }

    public List<Node> getAdjList(Node node) {
        return adjList.get(node);
    }

    public int getNumVertices() {
        return V;
    }

    public void colorGraph(int N) {
        // create a stack to store removed nodes
        Stack<Node> stack = new Stack<>();

        // loop over all nodes to remove nodes with degree < N
        for (Node node : adjList.keySet()) {
            if (adjList.get(node).size() < N) {
                stack.push(node);
            }
        }

        // remove a node to spill if all nodes have degree >= N
        if (stack.isEmpty()) {
            for (Node node : adjList.keySet()) {
                if (node.getColor().equals("white")) {
                    stack.push(node);
                    break;
                }
            }
        }

        // color the remaining nodes
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            Set<String> usedColors = new HashSet<>();

            // check the colors of connected nodes
            for (Node neighbor : adjList.get(node)) {
                if (!neighbor.getColor().equals("white")) {
                    usedColors.add(neighbor.getColor());
                }
            }

            // assign a color to the node that is different from connected nodes
            for (String color : getAvailableColors(N)) {
                if (!usedColors.contains(color)) {
                    node.setColor(color);
                    break;
                }
            }
        }
    }

    // utility method to get available colors
    private List<String> getAvailableColors(int N) {
        List<String> colors = new ArrayList<>(N);

        for (int i = 1; i <= N; i++) {
            colors.add("color" + i);
        }

        return colors;
    }
}
