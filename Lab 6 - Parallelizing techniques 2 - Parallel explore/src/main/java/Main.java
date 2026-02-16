import java.util.*;
import java.util.stream.Collectors;

public class Main {
    private static int CYCLE_LENGTH = 0;
    private static Node STARTING_NODE;
    private static final Random random = new Random();

    public static void main(String[] args) throws InterruptedException {

//        List<Node> graph = generateGraph2();
        List<Node> graph = randomGraph(50, 500);

//        List<Integer> list = new ArrayList<>();
//        for (int i = 0; i < 30; i++) {
//            int finalI = i;
//            (new Thread(() -> {
//                (new Thread(() -> {
//                    list.add(finalI * 30);
//                    System.out.println("legit" + finalI);
//                })).start();
//                list.add(finalI);
//                System.out.println("lmao" + finalI);
//            })).start();
//        }
//        Thread.sleep(2000);
//        System.out.println(list.size());

        STARTING_NODE = graph.get(2);
        CYCLE_LENGTH = graph.size();
        Map<Integer, Boolean> visited = new HashMap<>();
        List<Node> path = new ArrayList<>();

//        System.out.println(graph);
        for (Node node : graph) {
            System.out.println(node.getId() + ": " + node.getNeighbours().stream().map(Node::getId).collect(Collectors.toList()));
        }

        findCycle(STARTING_NODE, path, visited, 0 );
    }

    private static List<Node> randomGraph(int nodesCount, int edgesCount) {
        List<Node> graph = new ArrayList<>();

        for (int i = 0; i < nodesCount; i++) {
            graph.add(new Node(i, new ArrayList<>()));
        }

        for (int i = 0; i < edgesCount; i++) {
            int from = random.nextInt(nodesCount);
            int to = random.nextInt(nodesCount);
            while (from == to || graph.get(from).getNeighbours().contains(graph.get(to))) {
                from = random.nextInt(nodesCount);
                to = random.nextInt(nodesCount);
            }
            graph.get(from).getNeighbours().add(graph.get(to));
        }

        return graph;
    }

    private static void findCycle(Node node, List<Node> path, Map<Integer, Boolean> visited, int depth) {
        path.add(node);
        if (path.size() == CYCLE_LENGTH && node.getNeighbours().contains(STARTING_NODE)) {
            path.add(STARTING_NODE);
            System.out.println(path);
            return;
        }
        visited.put(node.getId(), true);
        if (depth < 3){
            node.getNeighbours().forEach(neighbour -> (new Thread(() -> {
                if (!visited.containsKey(neighbour.getId())) {
                    findCycle(neighbour, new ArrayList<>(path), new HashMap<>(visited), depth + 1);
                }
            })).start());
        } else {
            node.getNeighbours().forEach(neighbour -> {
                if (!visited.containsKey(neighbour.getId())) {
                    findCycle(neighbour, new ArrayList<>(path), new HashMap<>(visited), depth + 1);
                }
            });
        }
    }

    private static List<Node> generateGraph() {
        List<Node> graph = new ArrayList<>();
        Node prev = new Node(0, new ArrayList<>());
        Node first = prev;
        graph.add(prev);

        for (int i = 1; i < 5; i++) {
            Node node = new Node(i, new ArrayList<>());
            node.getNeighbours().add(prev);
            prev = node;
            graph.add(node);
        }

        first.getNeighbours().add(prev);
        return graph;
    }

    private static List<Node> generateGraph2() {
        List<Node> graph = generateGraph();
        graph.get(0).getNeighbours().add(graph.get(2));
        graph.get(1).getNeighbours().add(graph.get(3));
        return graph;
    }
}
