import java.util.Deque;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;

public class Channel implements Callable<Boolean> {

    private static String TAG = "Channel";
    private final Random randomizer = new Random();
    Message message;

    public Channel(Message message) {
        this.message = message;
    }

    @Override
    public Boolean call() throws Exception {
        try {
            int timeDelay = randomizer.nextInt(12);
            Thread.sleep(timeDelay);
            updateMessageQueue();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    public synchronized void updateMessageQueue() {
        synchronized (DSystem.channelMessages) {
            Queue<Message> messages =  DSystem.channelMessages.get(message.receiver.nodeIndex);
            messages.add(message);
            DSystem.channelMessages.put(message.receiver.nodeIndex, messages);
        }
    }
}
