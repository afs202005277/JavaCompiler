package pt.up.fe.comp2023;

/*
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.graph.Graph;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.graphicGraph.stylesheet.StyleConstants.FillMode;
import org.graphstream.ui.graphicGraph.stylesheet.StyleConstants.Units;
import org.graphstream.ui.graphicGraph.stylesheet.StyleSheet;
*/

import java.lang.reflect.Array;
import java.util.*;

public class InterferenceGraph {
    private Map<InterferenceGraphNode, List<InterferenceGraphNode>> adjList;

    public InterferenceGraph() {
        adjList = new HashMap<>();
    }

    public Set<InterferenceGraphNode> getNodes() {
        return adjList.keySet();
    }

    public void addInterferenceGraphNode(InterferenceGraphNode node) {
        for (Map.Entry<InterferenceGraphNode, List<InterferenceGraphNode>> entry : adjList.entrySet()) {
            if (Objects.equals(entry.getKey().getRegister(), node.getRegister())) {
                return;
            }
        }
        adjList.put(node, new LinkedList<>());
    }

    public int numNodes() {
        return adjList.size();
    }

    public void addEdge(String node1, String node2) {
        InterferenceGraphNode new1 = null;
        InterferenceGraphNode new2 = null;
        for (Map.Entry<InterferenceGraphNode, List<InterferenceGraphNode>> entry : adjList.entrySet()) {
            if (Objects.equals(entry.getKey().getRegister(), node1)) {
                new1 = entry.getKey();
            }
            if (Objects.equals(entry.getKey().getRegister(), node2)) {
                new2 = entry.getKey();
            }

            if (new1 != null && new2 != null)
                break;
        }

        if (new1 != null && new2 != null)
            addEdge(new1, new2);
    }

    public void addEdge(InterferenceGraphNode node1, InterferenceGraphNode node2) {
        if (!adjList.get(node1).contains(node2)) {
            adjList.get(node1).add(node2);
            adjList.get(node2).add(node1);
        }
    }

    public List<InterferenceGraphNode> getAdjacentInterferenceGraphNodes(InterferenceGraphNode node) {
        return adjList.get(node);
    }

/*
    public void visualizeGraph() {
        Graph graph = new SingleGraph("Interference Graph");

        for (Map.Entry<InterferenceGraphNode, List<InterferenceGraphNode>> entry : adjList.entrySet()) {
            org.graphstream.graph.Node graphNode = graph.addNode(entry.getKey().getRegister());
            graphNode.setAttribute("label", entry.getKey().getRegister());

            // Set the fill color based on the node's color
            if (entry.getKey().getColor().equals("black")) {
                graphNode.setAttribute("ui.style", "fill-color: black;");
            } else if (entry.getKey().getColor().equals("color1")) {
                graphNode.setAttribute("ui.style", "fill-color: red;");
            } else if (entry.getKey().getColor().equals("color2")) {
                graphNode.setAttribute("ui.style", "fill-color: blue;");
            } else if (entry.getKey().getColor().equals("color3")) {
                graphNode.setAttribute("ui.style", "fill-color: yellow;");
            } else if (entry.getKey().getColor().equals("color4")) {
                graphNode.setAttribute("ui.style", "fill-color: green;");
            } else if (entry.getKey().getColor().equals("color5")) {
                graphNode.setAttribute("ui.style", "fill-color: orange;");
            }
        }

        for (Map.Entry<InterferenceGraphNode, List<InterferenceGraphNode>> entry : adjList.entrySet()) {
            for (InterferenceGraphNode n : entry.getValue()) {
                if (graph.getEdge(n.getRegister() + "-" + entry.getKey().getRegister()) == null)
                    graph.addEdge(entry.getKey().getRegister() + "-" + n.getRegister(), entry.getKey().getRegister(), n.getRegister());
            }
        }

        Viewer viewer = graph.display();
        viewer.setCloseFramePolicy(Viewer.CloseFramePolicy.HIDE_ONLY);
    }
*/


    public int colorGraph(int maxNumColors) {
        ArrayList<String> colors = new ArrayList<>();
        colors.add("black");

        for (int i = 1; i < maxNumColors; i++) {
            colors.add("color" + i);
        }

        Map<InterferenceGraphNode, List<InterferenceGraphNode>> temp_adjList = new HashMap<>(adjList);
        Stack<InterferenceGraphNode> removed_nodes = new Stack<>();

        boolean was_removed = false;
        for (Map.Entry<InterferenceGraphNode, List<InterferenceGraphNode>> entry : temp_adjList.entrySet()) {
            if (entry.getValue().size() < maxNumColors) {
                removed_nodes.push(entry.getKey());
                for (Map.Entry<InterferenceGraphNode, List<InterferenceGraphNode>> entry_neighbour : temp_adjList.entrySet()) {
                    List<InterferenceGraphNode> values = entry_neighbour.getValue();
                    values.remove(entry.getKey());
                    temp_adjList.put(entry.getKey(), values);
                }
                was_removed = true;
            } else {
                return -1;
            }
        }

        if (!was_removed)
            return -1;

        int colorsUsed = 0;

        while (!removed_nodes.empty()) {
            InterferenceGraphNode checking_node = removed_nodes.pop();
            ArrayList<Integer> used_colors = new ArrayList<>();
            for (int j = 0; j < maxNumColors; j++) {
                used_colors.add(j);
            }

            for (InterferenceGraphNode node : adjList.get(checking_node)) {
                used_colors.remove((Object) colors.indexOf(node.getColor()));
            }

            if (used_colors.isEmpty())
                return -1;

            if (colorsUsed < used_colors.get(0) + 1)
                colorsUsed = used_colors.get(0) + 1;

            checking_node.setColor(colors.get(used_colors.get(0)));
            adjList.put(checking_node, adjList.get(checking_node));
        }

        return colorsUsed;
    }


    private List<String> getAvailableColors(int numColors) {
        List<String> colors = new ArrayList<>(numColors);

        for (int i = 1; i <= numColors; i++) {
            colors.add("color" + i);
        }

        return colors;
    }
}
