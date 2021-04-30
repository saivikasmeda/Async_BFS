import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DSystem {
    public static Map<Integer, Queue<Message>> channelMessages = new ConcurrentHashMap<Integer, Queue<Message>>();
    public static AtomicInteger messageCount = new AtomicInteger(0);

    public synchronized static void incrementMessageCount(){
        messageCount.addAndGet(1);
    }

    public static Node rootNode;
    public static String TAG = "DSystem";
    public int n = 0;
    public HashMap<Integer, Set<Integer>> configMap = null;
    public String[] ids = null;    // id's [101, 103, 105, 109]
    public List<Node> nodes = null;  // node objects for each process.
    private static DSystem system;
    public static DSystem getInstance() {
        if (system == null) {
            system = new DSystem();
        }
        return system;
    }

    public void configureSystem (String configFile) throws IOException {
        File file = new File(configFile);
        BufferedReader reader = new BufferedReader(new FileReader(file));
        List<String> lines = new ArrayList<>();
        String line = "";
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }

        if (lines.size() == 0) {
            System.out.println("File is empty. Please provide valid file.");
            return;
        }

        n = Integer.parseInt(lines.get(0));
        if (lines.size() != n+3) {
            System.out.println("Invalid configuration file. Please provide valid config file.");
            return;
        }

        ids = lines.get(1).split(" +");
        if (ids.length != n) {
            System.out.println("Number of IDs in the config file does not match the number of processes.");
            return;
        }
        int rootId = Integer.parseInt(lines.get(2));
        lines.remove(0);
        lines.remove(0);
        lines.remove(0);
        configMap = new HashMap<>();
        nodes = new ArrayList<>();
        for (int i=0; i<lines.size(); i++) {
            // Each line is the list of neighbors if the ith process.
            int ID = Integer.parseInt(ids[i]);
            String[] neighbors = lines.get(i).split(" +");

            // Store all the neighbours of this ith process in a map.
            Set<Integer> neighborsSet = new HashSet<>();
            for (int j=0; j<n; j++) {
                if (Integer.parseInt(neighbors[j]) == 1) {
                    neighborsSet.add(j);
                }
            }
            configMap.put(i, neighborsSet);
            channelMessages.put(i, new ArrayBlockingQueue<Message>(1000));
            // Create a node object for every ith process and store in array
            Node node = new Node(ID, i, new HashSet<>(neighborsSet));
            nodes.add(node);
            if(node.UID == rootId) {
                rootNode = node;
                node.distanceToRoot = 0;
            }
        }
    }

    public void executeRounds () throws ExecutionException, InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(n);

        List<Future<Integer>> results = new ArrayList<>();
        for (Node node: nodes) {
            Future<Integer> result = service.submit(node);
            results.add(result);
        }
        // Check if all processes have completed their task successfully or not.
        int successfulProcesses = 0;
        for (Future<Integer> result: results) {
            if (101 <= result.get() && result.get() <= 108) successfulProcesses++;
        }
        if (successfulProcesses != n)  {
           System.out.println("Round failed. Aborting system.");
        }else{
            System.out.println("No of messages Boardcasted: " + messageCount);
        }
        service.shutdown();

        displayParentChild();
        return;
    }

    public void displayParentChild(){
        for( Node node : nodes){
            if(node.UID != rootNode.UID)
                System.out.println("Process: " + node.UID + " Parent Process: " + node.parentNode.UID + " located: " + node.position + " nack " + node.nackNeighbours + " done " + node.doneNeighbours);
            else
                System.out.println("Process: "+ node.UID +" Parent Process: "+ node.parentNode + " located: "+ node.position+" nack " + node.nackNeighbours + " done " + node.doneNeighbours);

        }
        System.exit(0);
    }
    public synchronized Node getNeighbourNode(Integer neighbor) {
        return nodes.get(neighbor);
    }

}
