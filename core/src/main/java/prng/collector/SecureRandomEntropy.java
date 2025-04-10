package prng.collector;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import prng.config.Config;

/**
 * Collect entropy from specified secure random instances.
 *
 * @author Simon Greatrix on 02/04/2018.
 */
public class SecureRandomEntropy extends EntropyCollector {

  private class Runner implements Runnable {

    final byte[] data = new byte[256];

    int index;


    @Override
    public void run() {
      randoms[index].nextBytes(data);
      entropyQueue.add(this);
    }

  }



  private final SecureRandom[] randoms;

  private BlockingQueue<Runner> entropyQueue;

  private Executor service;


  public SecureRandomEntropy(Config config) {
    super(config, 50);

    String names = config.get("algorithms");
    String[] names2 = names.split(",");
    SecureRandom[] tmp = new SecureRandom[names2.length];
    int k = 0;
    for (String n : names2) {
      String p, a;
      int j = n.indexOf('/');
      if (j == -1) {
        p = "";
        a = n;
      } else {
        p = n.substring(0, j);
        a = n.substring(j + 1);
      }

      try {
        if (!p.isEmpty()) {
          tmp[k] = SecureRandom.getInstance(a, p);
        } else {
          tmp[k] = SecureRandom.getInstance(a);
        }
        k++;
      } catch (NoSuchProviderException e) {
        LOG.error("Invalid secure random specification for entropy collector: {}. The provider is unknown.", n);
      } catch (NoSuchAlgorithmException e) {
        LOG.error("Invalid secure random specification for entropy collector: {}. The algorithm is unknown.", n);
      }
    }

    randoms = new SecureRandom[k];
    System.arraycopy(tmp, 0, randoms, 0, k);
  }


  @Override
  protected boolean initialise() {
    if (randoms.length == 0) {
      return false;
    }

    entropyQueue = new LinkedBlockingQueue<>();
    service = new ThreadPoolExecutor(
        randoms.length, randoms.length, 2L * getBaseDelay(), TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
        new DaemonThreadFactory("PRNG-collect-secure-random")
    );
    ((ThreadPoolExecutor) service).allowCoreThreadTimeOut(true);
    for (int i = 0; i < randoms.length; i++) {
      Runner r = new Runner();
      r.index = i;
      service.execute(r);
    }
    return true;
  }


  @Override
  protected void runImpl() {
    Runner r = entropyQueue.poll();
    if (r != null) {
      setEvent(r.data);
      service.execute(r);
    }
  }

}
