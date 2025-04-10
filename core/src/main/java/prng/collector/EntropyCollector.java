package prng.collector;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import prng.EntropySource;
import prng.Fortuna;
import prng.LoggersFactory;
import prng.config.Config;

/**
 * Entropy source that can pull entropy from some source on a regular basis feeds into Fortuna.
 *
 * @author Simon Greatrix
 */
public abstract class EntropyCollector extends EntropySource
    implements Runnable {

  /**
   * Logger for entropy collectors
   */
  protected static final Logger LOG = LoggersFactory.getLogger(EntropyCollector.class);

  /** Should collection be suspended if no entropy was used and the speed is already at the minimum?. */
  private static final boolean ALLOW_SUSPEND;

  /** The maximum ratio of the collection speed to the base speed. This is the slowest it can go. */
  private static final double MAX_RATIO;

  /** The minimum ratio of the collection speed to the base speed. This the fastest it can go. */
  private static final double MIN_RATIO;

  /**
   * Scheduler for entropy gathering processes
   */
  private static final ScheduledExecutorService SERVICE = Executors.newSingleThreadScheduledExecutor(
      new DaemonThreadFactory("PRNG-EntropyCollector"));

  /**
   * Period over which entropy collection slows down.
   */
  private static final long SLOW_DOWN_PERIOD;

  /**
   * Set of all known collectors
   */
  private static final Set<EntropyCollector> SOURCES = new HashSet<>();

  /**
   * Is entropy collection suspended?
   */
  private static boolean IS_SUSPENDED = false;

  /**
   * Slow down ratio.
   */
  private static double ratio = 1.0;

  private static final Runnable SPEED_CHECK = new Runnable() {
    @Override
    public void run() {
      synchronized (SOURCES) {
        Fulfillment fulfillment = Fortuna.getFulfillment();
        long provided = fulfillment.provided;
        long used = fulfillment.used;
        long excess = fulfillment.excess;

        // If we are in debt, keep collecting at normal rate
        if (excess < 0) {
          LOG.info("Provided {} bytes of entropy and used {}, with an entropy debt of {}.", provided, used, -excess);
          ratio = 1.0;
          return;
        }

        // check edge condition
        if (used == 0) {
          if (ALLOW_SUSPEND && (1.05 * ratio >= MAX_RATIO)) {
            if (excess > 0) {
              if (!IS_SUSPENDED) {
                // No entropy was used, and we have an excess. We can stop collecting.
                LOG.info("No entropy used, {} bytes provided", provided);
                ratio = 1.0;
                suspend();
              }
              LOG.debug("No entropy used. No entropy needed. System already suspended.");
              return;
            }

            // None used but we are in debt. Keep collecting at default rate.
            LOG.info("No entropy used, {} bytes provided, working to remove debt of {}", provided, -excess);
            ratio = 1.0;
            return;
          }

          // can't suspend, so slow down by increasing the ratio
          double oldRatio = ratio;
          if (Math.abs(oldRatio - MAX_RATIO) < 4 * Math.ulp(MAX_RATIO)) {
            if (provided != 0) {
              LOG.info("Entropy fulfillment ratio was 0 out of {}. Ratio unchanged.", provided);
            }
            return;
          }

          double newRatio = Math.min(MAX_RATIO, 1.05 * ratio);
          ratio = newRatio;
          LOG.info("Entropy fulfillment ratio was {} out of {}. Changed delay ratio from {} to {}.", used, provided, oldRatio, newRatio);
          return;
        }

        if (provided == 0 && used > 0) {
          // No entropy was added, but some was used. We should restart.
          LOG.info("No bytes provided. {} bytes used", used);
          ratio = 1.0;
          restart();
          return;
        }

        // What delay ratio should we have used?
        double oldRatio = ratio;

        // if we added less than we used, we have to speed up, which means making the delay ratio smaller.
        double requiredRatio = ratio * provided / used;

        // Nudge the ratio towards this
        double newRatio = 0.95 * oldRatio + 0.05 * requiredRatio;

        // Never go below min - we slow down, but we do not speed up.
        ratio = Math.min(MAX_RATIO, Math.max(MIN_RATIO, newRatio));

        LOG.info("Entropy fulfillment ratio was {} out of {}. Changed delay ratio from {} to {}.", used, provided, oldRatio, newRatio);
      }
    }
  };



  /** Report of entropy fulfillment. */
  public static class Fulfillment {

    /** Cumulative backlog in entropy collection. */
    public long excess;

    /** Amount of entropy provided this period. */
    public long provided;

    /** Amount of entropy used this period. */
    public long used;

  }


  /**
   * Initialise an entropy source. This allows an entropy source to register with the scheduler.
   *
   * @param es the entropy source
   */
  public static void initialise(EntropyCollector es) {
    LOG.info("Initialising entropy collector {}", es.getClass().getName());
    synchronized (SOURCES) {
      SOURCES.add(es);
      if (IS_SUSPENDED) {
        return;
      }
    }

    es.start();
  }


  /**
   * Initialise standard entropy gathering. This is configured in the configuration properties: <p>
   *
   * <dl> <dt>collector.<i>class.name</i> = [boolean] <dd>If true, indicates that a collector of type <i>class.name</i> should be created.
   * <dt>config.<i>class.name</i>.x = y <dd>Configuration with prefix config.<i>class.name</i> is passed to the collectors <code>initialise</code> method.
   * </dl>
   */
  private static void initialiseStandard() {
    Config config = Config.getConfig("collector");
    for (String cl : config) {
      // is collector active?
      if (!config.getBoolean(cl, true)) {
        continue;
      }

      try {
        // create collector
        Class<?> clazz1 = Class.forName(cl);
        Class<? extends EntropyCollector> clazz2 = clazz1.asSubclass(
            EntropyCollector.class);
        Constructor<? extends EntropyCollector> cons = clazz2.getConstructor(Config.class);

        // get configuration
        Config ecConfig = Config.getConfig("config." + cl);
        EntropyCollector ec = cons.newInstance(ecConfig);

        // register and initialise collector
        initialise(ec);
      } catch (ClassNotFoundException cnfe) {
        LOG.error("Class {} is not available", cl, cnfe);
      } catch (ClassCastException cce) {
        LOG.error("Class {} is not a sub-class of EntropyCollector", cl, cce);
      } catch (InvocationTargetException | InstantiationException
               | IllegalAccessException e) {
        LOG.error("Class {} could not be instantiated", cl, e);
      } catch (NoSuchMethodException e) {
        // It really should have a constructor :-)
        LOG.error("Class {} does not have a constructor that takes an instance of Config", cl, e);
      }
    }
  }


  /**
   * Restart all EntropyCollectors
   */
  public static void restart() {
    LOG.info("Entropy collection has been restarted");
    synchronized (SOURCES) {
      if (!IS_SUSPENDED) {
        return;
      }

      for (EntropyCollector ses : SOURCES) {
        ses.start();
      }
      IS_SUSPENDED = false;
    }
  }


  /**
   * Suspend all EntropyCollectors
   */
  public static void suspend() {
    LOG.info("Entropy collection has been suspended");
    synchronized (SOURCES) {
      IS_SUSPENDED = true;
      for (EntropyCollector ses : SOURCES) {
        ses.cancel();
      }
    }
  }


  static {
    initialiseStandard();

    Config config = Config.getConfig("", EntropyCollector.class);
    SLOW_DOWN_PERIOD = config.getLong("slowDownPeriod", 5000);

    MIN_RATIO = Math.max(0.0001, config.getDouble("minRatio", 1.0));
    MAX_RATIO = Math.max(MIN_RATIO, config.getDouble("maxRatio", 1000.0));
    ALLOW_SUSPEND = config.getBoolean("allowSuspend", true);

    SERVICE.scheduleAtFixedRate(SPEED_CHECK, SLOW_DOWN_PERIOD, SLOW_DOWN_PERIOD, TimeUnit.MILLISECONDS);
  }

  /**
   * Delay in milliseconds between entropy collections
   */
  private final int delay;

  /**
   * The future entropy collection
   */
  private ScheduledFuture<?> future = null;


  /**
   * Create new entropy collector
   *
   * @param config    configuration for this collector
   * @param dfltDelay the default collection delay
   */
  protected EntropyCollector(Config config, int dfltDelay) {
    delay = Math.max(1, config.getInt("delay", dfltDelay));
  }


  /**
   * Cancel this entropy collection
   */
  private synchronized void cancel() {
    ScheduledFuture<?> oldFuture = future;
    future = null;
    if (oldFuture != null) {
      oldFuture.cancel(false);
    }
  }


  /**
   * Get the base delay between milliseconds in milliseconds.
   *
   * @return the delay
   */
  protected final int getBaseDelay() {
    return delay;
  }


  /**
   * Get the delay between invocations in milliseconds. This may increase if entropy collection is backing off.
   *
   * @return requested delay
   */
  protected final int getDelay() {
    return (int) (delay * ratio);
  }


  /**
   * Initialise this collector
   *
   * @return true if the collector is operational, false if it is unusable
   */
  protected abstract boolean initialise();


  /**
   * Collect some entropy and schedule the next collection.
   */
  @Override
  public void run() {
    try {
      runImpl();
    } catch (RuntimeException re) {
      LOG.error("Error during entropy collection", re);
    }
    int myDelay = getDelay();

    // Only reschedule if not suspended.
    if (!IS_SUSPENDED) {
      SERVICE.schedule(this, myDelay, TimeUnit.MILLISECONDS);
    }
  }


  /**
   * Generate entropy.
   */
  protected abstract void runImpl();


  /**
   * Start this entropy collector
   */
  private synchronized void start() {
    boolean isOK = initialise();
    if (!isOK) {
      return;
    }

    cancel();
    future = SERVICE.schedule(this, getDelay(), TimeUnit.MILLISECONDS);
  }


  /**
   * Stop this collector and remove it from the list of known collectors.
   */
  public void stop() {
    cancel();
    synchronized (SOURCES) {
      SOURCES.remove(this);
    }
  }

}
