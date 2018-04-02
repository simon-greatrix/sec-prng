package prng.collector;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import prng.config.Config;

/**
 * Use current thread CPU time and User time as a source of entropy
 *
 * @author Simon Greatrix
 */
public class ThreadTimeEntropy extends EntropyCollector {

  /**
   * Create a collector that uses allocation of thread times to produce entropy
   *
   * @param config configuration for this
   */
  public ThreadTimeEntropy(Config config) {
    super(config, 100);
  }


  @Override
  protected boolean initialise() {
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    if (!bean.isThreadCpuTimeSupported()) {
      return false;
    }

    long[] ids = bean.getAllThreadIds();
    for (long id : ids) {
      try {
        bean.getThreadCpuTime(id);
        bean.getThreadUserTime(id);
      } catch (UnsupportedOperationException e) {
        return false;
      }
    }

    return true;
  }


  @Override
  protected void runImpl() {
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    if (!bean.isThreadCpuTimeEnabled()) {
      return;
    }
    long[] ids = bean.getAllThreadIds();
    long sum = 0;
    for (long id : ids) {
      long t1, t2;
      try {
        t1 = bean.getThreadCpuTime(id);
        t2 = bean.getThreadUserTime(id);
      } catch (UnsupportedOperationException e) {
        // should not happen as we tested earlier if they were supported
        return;
      }
      if (t1 != -1) {
        sum = sum * 31 + t1;
      }
      if (t2 != -1) {
        sum = sum * 31 + t2;
      }
    }
    setEvent((int) sum);
  }

}