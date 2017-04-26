package prng.collector;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A creator of daemon threads for the PRNG background processes.
 * 
 * @author Simon
 *
 */
public class DaemonThreadFactory implements ThreadFactory {
    /** Generator for unique ID numbers for the threads */
    private AtomicInteger idSrc_ = new AtomicInteger();

    /** Thread name prefix */
    private final String name_;


    /**
     * Create a new factory
     * 
     * @param name
     *            the name prefix for threads
     */
    public DaemonThreadFactory(String name) {
        name_ = name;
    }


    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, name_ + "-" + idSrc_.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

}
