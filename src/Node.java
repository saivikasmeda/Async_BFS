import java.util.*;
import java.util.concurrent.*;

public class Node implements Callable<Integer> {

    public static String TAG = "Node";
    Message.MessageType nodeStatus  = Message.MessageType.READY;
    Integer UID = null;
    String position;
    int nodeIndex;
    int distanceToRoot = Integer.MAX_VALUE;
    final Set<Integer> neighbours;
    Node parentNode = null;
    Set<Integer> nackNeighbours = new HashSet<Integer>();
    Set<Integer> doneNeighbours = new HashSet<>();
    Boolean flag = true;
    private final DSystem system;
    Node rootNode;
    public Node (int id, int index, Set<Integer> neighs) {
        UID = id;
        nodeIndex = index;
        system = DSystem.getInstance();
        neighbours = neighs;
    }

    @Override
    public Integer call() throws Exception {
        try {
            int result = startAsyBFSAlgorithm();
            return result;
        } catch (Exception e) {
//            Printer.debug(TAG,String.format("P%s round failed. Exception: %s", nodeIndex, e.getMessage()));
            e.printStackTrace();
            return -1;
        }
    }

    private int startAsyBFSAlgorithm() throws Exception {
//        while(nodeStatus != Message.MessageType.DONE || DSystem.channelMessages.get(this.nodeIndex).size() != 0 ) {
        synchronized (DSystem.rootNode){
            rootNode = DSystem.rootNode;
        }
        while(nodeStatus != Message.MessageType.DONE){
            getMessageFromChannel();
            if(nodeStatus != Message.MessageType.DONE)
                checkNeighbourNode();
        }
        setNackAndDone();
        return this.UID;
    }

    private Message getMessageToSend(int distance, Node neighbour, Message.MessageType messageType) {
        Message message = new Message();
        message.distance = distance;
        message.sender = this;
        message.receiver = neighbour;
        message.message = messageType;
        return message;
    }
    public void getMessageFromChannel() throws Exception{
        if(this == rootNode && flag){
            flag = false;
            transition("send");
        }
        transition("receive");
    }


    private void transition(String mode) throws Exception {
        switch (mode){
            case "send":

                List<Future<Boolean>> deliveries = new ArrayList<>();
                ExecutorService service = Executors.newFixedThreadPool(neighbours.size());
                for (Integer neigh : neighbours) {
                    Node neighbour = system.getNeighbourNode(neigh);
                    if(parentNode != neighbour) {
                        Message messageToSend = getMessageToSend(distanceToRoot + 1, neighbour, Message.MessageType.EXPLORE);
                        Channel channel = new Channel(messageToSend);
                        deliveries.add(service.submit(channel));
                        DSystem.incrementMessageCount();
                    }else{
                        Message messageToSend = getMessageToSend(distanceToRoot, neighbour, Message.MessageType.ACK);
                        Channel channel = new Channel(messageToSend);
                        deliveries.add(service.submit(channel));
                        DSystem.incrementMessageCount();
                    }
                }
                int successfulProcesses = 0;
                for (Future<Boolean> result: deliveries) {
                    if (result.get()) successfulProcesses++;
                }
                while(successfulProcesses != neighbours.size()){

                }
                service.shutdown();
                break;

            case "receive":

                Queue<Message> messages = getmessages();
                for( Message message : messages) {
                    if (message.message == Message.MessageType.EXPLORE) {
                        if (distanceToRoot > message.distance) {
                            distanceToRoot = message.distance;
                            parentNode = message.sender;
                            if(this == rootNode){
                                System.out.println("sending again");
                            }
                            transition("send");
                        } else
                            sendNackMessageToNeighbour(message.sender);
                    }

                    if (message.message == Message.MessageType.DONE )
                        doneNeighbours.add(message.sender.UID);

                    if (message.message == Message.MessageType.NACK)
                        nackNeighbours.add(message.sender.UID);

                    if (this == rootNode &&  doneNeighbours.size() == neighbours.size() )
                        this.nodeStatus = Message.MessageType.DONE;

                    else {
                        Set<Integer> unique = new HashSet<>();
                        // first 104 received msg from 102 and forwarded to 101 then 101 sends nack to 104. later 101 sends msg and updates it then we've to remove 101 from nack
                        if (parentNode != null)
                            nackNeighbours.remove(parentNode.UID);
                        unique.addAll(nackNeighbours);
                        unique.addAll(doneNeighbours);

                        if (unique.size() >= neighbours.size() - 1) {
                            setProcessPosition();
                            if (this.UID != rootNode.UID) {
                                sendDoneMessageToParent();
                            }
                        }
                    }
                }
                break;
        }
    }


    public void checkNeighbourNode() throws Exception{
        Boolean neigFlag = true;
        for (Integer neig : neighbours) {
            Node neighbour = system.getNeighbourNode(neig);
            if (neighbour != parentNode && neighbour.nodeStatus != Message.MessageType.DONE) {
                neigFlag = false;
            }
        }
        if (neigFlag && this != rootNode) {
            setProcessPosition();
            sendDoneMessageToParent();
        }else if(neigFlag)
            nodeStatus = Message.MessageType.DONE;
    }


    public void setProcessPosition(){
        if (doneNeighbours.size() != 0) this.position = "Middle Node";
        else this.position = "leaf Node";
    }

    public void sendDoneMessageToParent() throws Exception{
        List<Future<Boolean>> deliveriesDone = new ArrayList<>();
        ExecutorService serviceDone = Executors.newFixedThreadPool(1);

        Message messageToSend = getMessageToSend(distanceToRoot, parentNode, Message.MessageType.DONE);
        Channel channel = new Channel(messageToSend);
        deliveriesDone.add(serviceDone.submit(channel));
        DSystem.incrementMessageCount();

        int successfulProcesses = 0;
        for (Future<Boolean> result: deliveriesDone) {
            if (result.get()) successfulProcesses++;
        }
        while(successfulProcesses != 1){
            System.out.println("waiting to deliver Done message");
        }
        nodeStatus = Message.MessageType.DONE;
        serviceDone.shutdown();

    }

    public void sendNackMessageToNeighbour(Node receiver) throws Exception{
        List<Future<Boolean>> deliveriesNack = new ArrayList<>();
        ExecutorService serviceNack = Executors.newFixedThreadPool(1);

        Message messageToSend = getMessageToSend(distanceToRoot, receiver, Message.MessageType.NACK);
        Channel channel = new Channel(messageToSend);
        deliveriesNack.add(serviceNack.submit(channel));
        DSystem.incrementMessageCount();
        int successfulProcesses = 0;
        for (Future<Boolean> result: deliveriesNack) {
            if (result.get()) successfulProcesses++;
        }
        while(successfulProcesses != 1){
            System.out.println("waiting deliver Nack message");
        }
        serviceNack.shutdown();
    }


    public synchronized Queue<Message> getmessages(){
        synchronized (DSystem.channelMessages) {
            Queue<Message> messages = DSystem.channelMessages.get(nodeIndex);
            DSystem.channelMessages.put(nodeIndex, new ArrayBlockingQueue<Message>(1000));
            return messages;
        }

    }

    public void setNackAndDone(){
        for (Integer neigh : neighbours){
            Node neighbour = system.getNeighbourNode(neigh);
            if(neighbour.parentNode == this){
                doneNeighbours.add(neighbour.UID);
            }else if(neighbour != this.parentNode){
                nackNeighbours.add(neighbour.UID);
            }
        }
    }


    @Override
    public String toString() {
        return "Node{" +
                "UID=" +UID+
                " IndexID=" + nodeIndex +
                " Edges=" + neighbours +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return UID == node.UID;
    }

    @Override
    public int hashCode() {
        return Objects.hash(UID);
    }
}
