package prng.collector;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Iterator;
import java.util.List;
import prng.config.Config;

/**
 * Use the amount of time spent collecting garbage as a source of entropy
 *
 * @author Simon Greatrix
 */
public class GarbageCollectorEntropy extends EntropyCollector {

  /** The VM's garbage collectors */
  private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();


  /**
   * Create a collector that uses garbage collection statistics to produce entropy
   *
   * @param config configuration for this
   */

  public GarbageCollectorEntropy(Config config) {
    super(config, 10000);
  }


  @Override
  protected boolean initialise() {
    // check at least one garbage collector can provide useful information
    Iterator<GarbageCollectorMXBean> iter = gcBeans.iterator();
    while (iter.hasNext()) {
      GarbageCollectorMXBean bean = iter.next();
      long l1 = bean.getCollectionCount();
      long l2 = bean.getCollectionTime();
      if ((l1 == -1) && (l2 == -1)) {
        iter.remove();
      }
    }
    return !gcBeans.isEmpty();
  }


  @Override
  protected void runImpl() {
    long sum = 0;
    for (GarbageCollectorMXBean garbageCollectorMXBean : gcBeans) {
      long l = garbageCollectorMXBean.getCollectionCount();
      if (l != -1) {
        sum = sum * 31 + l;
      }

      l = garbageCollectorMXBean.getCollectionTime();
      if (l != -1) {
        sum = sum * 31 + l;
      }
    }
    if (sum != 0) {
      setEvent((int) sum);
    }
  }
}