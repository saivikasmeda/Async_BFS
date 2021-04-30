import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Main {

    /**
     * 3
     * [12, 32, 66]
     * [0, 1, 1]
     * [1, 0, 1]
     * [1, 1, 0]
     */

    public static void main (String... args) throws Exception{
        DSystem system = DSystem.getInstance();
        system.configureSystem(args[0]);
        system.executeRounds();
    }

}
