public class Message {

    public enum MessageType {
        READY, EXPLORE, ACK, NACK, DONE;
    }

    Node sender;
    Node receiver;
    int distance;
    MessageType message;

    public Message(Node sender, Node receiver, int distance, MessageType message) {
        this.sender = sender;
        this.receiver = receiver;
        this.distance = distance;
        this.message = message;
    }

    public Message() {

    }
}
