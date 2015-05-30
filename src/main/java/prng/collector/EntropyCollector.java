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
import org.slf4j.LoggerFactory;

import prng.Config;
import prng.EntropySource;

/**
 * Entropy source that can pull entropy from some source on a regular basis
 * feeds into Fortuna.
 * 
 * @author Simon Greatrix
 *
 */
abstract public class EntropyCollector extends EntropySource implements
        Runnable {
    /** Is entropy collection suspended? */
    private static boolean IS_SUSPENDED = false;

    /** Logger for entropy collectors */
    protected static final Logger LOG = LoggerFactory.getLogger(EntropyCollector.class);

    /**
     * Scheduler for entropy gathering processes
     */
    private static ScheduledExecutorService SERVICE = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory(
            "PRNG-EntropyCollector"));

    /**
     * Set of all known collectors
     */
    private static Set<EntropyCollector> SOURCES = new HashSet<EntropyCollector>();


    /**
     * Initialise an entropy source. This allows an entropy source to register
     * with the scheduler.
     * 
     * @param es
     *            the entropy source
     */
    public static void initialise(EntropyCollector es) {
        synchronized (SOURCES) {
            SOURCES.add(es);
            if( IS_SUSPENDED ) return;
        }

        es.start();
    }


    /**
     * Initialise standard entropy gathering. This is configured in the
     * configuration properties:
     * <p>
     * 
     * <dl>
     * <dt>collector.<i>class.name</i> = [boolean]
     * <dd>If true, indicates that a collector of type <i>class.name</i> should
     * be created.
     * <dt>config.<i>class.name</i>.x = y
     * <dd>Configuration with prefix config.<i>class.name</i> is passed to the
     * collectors <code>initialise</code> method.
     * </dl>
     * 
     */
    private static void initialiseStandard() {        
        Config config = Config.getConfig("collector");
        for(String cl:config) {
            // is collector active?
            if( !config.getBoolean(cl, true) ) continue;

            try {
                // create collector
                Class<?> clazz1 = Class.forName(cl);
                Class<? extends EntropyCollector> clazz2 = clazz1.asSubclass(EntropyCollector.class);
                Constructor<? extends EntropyCollector> cons = clazz2.getConstructor(new Class<?>[] { Config.class });

                // get configuration
                Config ecConfig = Config.getConfig("config." + cl);
                EntropyCollector ec = cons.newInstance(ecConfig);

                // register and initialise collector
                initialise(ec);
            } catch (ClassNotFoundException cnfe) {
                LOG.error("Class " + cl + " is not available", cnfe);
            } catch (ClassCastException cce) {
                LOG.error("Class " + cl
                        + " is not a sub-class of EntropyCollector", cce);
            } catch (InvocationTargetException | InstantiationException
                    | IllegalAccessException e) {
                LOG.error("Class " + cl + " could not be instantiated", e);
            } catch (NoSuchMethodException e) {
                // It really should have a constructor :-)
                LOG.error("Class " + cl + " does not have a constructor"
                        + " that takes an instance of Config", e);
            }
        }
    }


    /**
     * Restart all EntropyCollectors
     */
    public static void restart() {
        synchronized (SOURCES) {
            if( !IS_SUSPENDED ) return;

            for(EntropyCollector ses:SOURCES) {
                ses.start();
            }
            IS_SUSPENDED = false;
        }
    }


    /**
     * Suspend all EntropyCollectors
     */
    public static void suspend() {
        synchronized (SOURCES) {
            IS_SUSPENDED = true;
            for(EntropyCollector ses:SOURCES) {
                ses.cancel();
            }
        }
    }

    static {
        initialiseStandard();
    }

    /** The future entropy collection */
    private ScheduledFuture<?> future_ = null;

    /** Delay in milliseconds between entropy collections */
    private final int delay_;


    /**
     * Create new entropy collector
     * 
     * @param config
     *            configuration for this collector
     * @param dfltDelay
     *            the default collection delay
     */
    protected EntropyCollector(Config config, int dfltDelay) {
        delay_ = config.getInt("delay", dfltDelay);
    }


    /** Cancel this entropy collection */
    private synchronized void cancel() {
        ScheduledFuture<?> future = future_;
        future_ = null;
        if( future != null ) future.cancel(false);
    }


    /**
     * Get the delay between invocations in milliseconds.
     * 
     * @return requested delay
     */
    protected int getDelay() {
        return delay_;
    }


    /**
     * Initialise this collector
     * 
     * @return true if the collector is operational, false if it is unusable
     */
    abstract protected boolean initialise();


    /**
     * Generate entropy.
     */
    @Override
    abstract public void run();


    /**
     * Start this entropy collector
     */
    private synchronized void start() {
        boolean isOK = initialise();
        if( !isOK ) return;

        cancel();
        future_ = SERVICE.scheduleWithFixedDelay(this, getDelay(), getDelay(),
                TimeUnit.MILLISECONDS);
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