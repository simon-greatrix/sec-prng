package prng.collector;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A creator of daemon threads for the PRNG background processes.
 *
 * @author Simon
 */
public class DaemonThreadFactory implements ThreadFactory {

  /** Thread name prefix */
  private final String name;

  /** Generator for unique ID numbers for the threads */
  private final AtomicInteger idSrc = new AtomicInteger();


  /**
   * Create a new factory
   *
   * @param name the name prefix for threads
   */
  public DaemonThreadFactory(String name) {
    this.name = name;
  }


  @Override
  public Thread newThread(Runnable r) {
    Thread thread = new Thread(r, name + "-" + idSrc.incrementAndGet());
    thread.setDaemon(true);
    return thread;
  }

}
