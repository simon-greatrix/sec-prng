package prng.collector;

import java.util.LinkedList;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A creator of daemon threads for the PRNG background processes.
 *
 * @author Simon
 */
public class DaemonThreadFactory implements ThreadFactory {

  /** IDs for re-use as it improves thread reports. */
  private final LinkedList<Integer> idReuse = new LinkedList<>();

  /** Generator for unique ID numbers for the threads */
  private final AtomicInteger idSrc = new AtomicInteger();

  /** Thread name prefix */
  private final String name;


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
    final Integer id;
    synchronized (idReuse) {
      if (idReuse.isEmpty()) {
        id = idSrc.incrementAndGet();
      } else {
        id = idReuse.removeFirst();
      }
    }

    Runnable wrapper = () -> {
      try {
        r.run();
      } finally {
        synchronized (idReuse) {
          idReuse.addLast(id);
        }
      }
    };

    Thread thread = new Thread(wrapper, name + "-" + id);
    thread.setDaemon(true);
    return thread;
  }

}
