package prng.collector;

import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import prng.config.Config;

/**
 * Source entropy from how long it takes to finalize objects.
 *
 * @author Simon Greatrix
 */
public class FinalizerEntropy extends EntropyCollector {

  private static final AtomicInteger COUNTER = new AtomicInteger();

  /** Number of objects held */
  private static final int HASH_SIZE = 31;



  class FinalizerEvent implements Runnable {

    /** Counter. */
    final int count = COUNTER.incrementAndGet();

    /** When this event was created */
    final long createTime = System.nanoTime();


    public void run() {
      byte[] data = new byte[64];
      ByteBuffer buffer = ByteBuffer.wrap(data);
      buffer.putLong(0, System.nanoTime() - createTime);
      buffer.putInt(8, hashCode());
      buffer.putInt(12, count);
      setEvent(data);
    }

  }

  /**
   * Array where objects are held for a while before they are garbage collected.
   */
  final Object[] objects = new Object[HASH_SIZE];

  private Cleaner cleaner = Cleaner.create(new DaemonThreadFactory("PRNG-FinalizerEntropy"));


  /**
   * Create new entropy collector
   *
   * @param config the configuration
   */
  public FinalizerEntropy(Config config) {
    super(config, 100);
  }


  @Override
  protected boolean initialise() {
    return true;
  }


  @Override
  protected void runImpl() {
    Object o = new Object();
    int hc = o.hashCode() % HASH_SIZE;
    objects[hc] = o;
    cleaner.register(o, new FinalizerEvent());
  }

}
