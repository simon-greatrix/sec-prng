package prng.server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Handle replies from the NetCache to the client
 * 
 * @author Simon Greatrix
 * 
 */
class Boss implements Runnable {

    /**
     * Awoken state, should only be accessed whilst lock2 is held.
     */
    private boolean awoken_ = false;

    /**
     * Outer lock on the selector. We use fair locking so no thread
     * is blocked for an unreasonable amount of time.
     */
    private final Lock lock1_ = new ReentrantLock(true);

    /**
     * Inner lock, should only be acquired whilst lock1 is held. Once acquired,
     * lock1 should be released. As lock1 is held, only one thread can ever be
     * waiting to acquire this lock.
     */
    private final Lock lock2_ = new ReentrantLock();

    /** The executors that handle incoming requests */
    protected final ExecutorService exec_;

    /** The channel selector */
    private final Selector selector_;

    /** Queue of new receivers the boss must register */
    private BlockingQueue<Communicator> updates_ = new LinkedBlockingQueue<Communicator>();


    /**
     * New Boss
     * 
     * @param name
     *            stem for worker threads
     */
    Boss(String name) {
        exec_ = new BMExecutor(name, Integer.MAX_VALUE, false);
        try {
            selector_ = Selector.open();
        } catch (IOException ioe) {
            throw new ExceptionInInitializerError(ioe);
        }
    }


    /**
     * Get the channel selector this boss uses
     * 
     * @return the selector
     */
    Selector getSelector() {
        return selector_;
    }


    /**
     * Connect the cache connection
     * 
     * @param conn
     *            the cache connection
     * @return a future which will be done when the connection attempt has run.
     */
    Future<?> connect(Connection conn) {
        return exec_.submit(conn);
    }


    /**
     * Update a communicator's relation to the selector
     * 
     * @param comm
     *            a communicator
     */
    void update(Communicator comm) {
        updates_.offer(comm);
        wakeup();
    }


    /**
     * Process the cache connections
     */
    @Override
    public void run() {
        while( true ) {
            try {
                Communicator comm;
                while( (comm = updates_.poll()) != null ) {
                    comm.updateNow();
                }

                try {
                    // Complicated locking for selection. Lock1 can be acquired
                    // by waiting threads, and this thread cannot re-acquire
                    // Lock2 without it.
                    lock1_.lock();
                    try {
                        lock2_.lock();
                    } finally {
                        lock1_.unlock();
                    }
                    try {
                        // do the select
                        int sel = awoken_ ? selector_.selectNow()
                                : selector_.select();
                        awoken_ = false;
                        if ( sel == 0 ) continue;
                    } finally {
                        lock2_.unlock();
                    }
                } catch (IOException e) {
                    ServerMain.LOG.error("CACHE_REPLY_SELECTION_FAILURE", e);
                }

                // process selected keys
                Set<SelectionKey> keys = selector_.selectedKeys();
                Iterator<SelectionKey> iter = keys.iterator();
                while( iter.hasNext() ) {
                    SelectionKey key = iter.next();
                    comm = (Communicator) key.attachment();

                    comm.process(exec_);
                    iter.remove();
                }
            } catch (RuntimeException re) {
                ServerMain.LOG.error("UNHANDLED_EXCEPTION_IN_CACHE_COMMUNICATIONS", re);
            } catch (OutOfMemoryError err) {
                // Back off for a while. The GC may eventually get somewhere.
                try {
                    // sleep 5 minutes in the hope things get better
                    Thread.sleep(300000);
                } catch (InterruptedException ie) {
                    // unexpected, but lets carry on
                }
            } catch (Error err) {
                // Try to log it. These Errors will not fix themselves so this
                // is indeed fatal
                try {
                    ServerMain.LOG.error("FATAL_ERROR_IN_CACHE_COMMUNICATIONS", err);
                } catch (Throwable thrown) {
                    err.printStackTrace();
                    thrown.printStackTrace();
                }
            }
        }
    }


    /**
     * Start the boss
     * 
     * @param name
     *            the boss thread's name
     */
    protected void start(String name) {
        Thread thread = new Thread(this, name);
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }


    /**
     * Wakeup the Boss thread
     */
    protected void wakeup() {
        // get lock1 so we can get lock2
        lock1_.lock();
        try {
            // get lock2 so we can set awoken
            if ( !lock2_.tryLock() ) {
                // No other thread can try to get lock2 as we have lock1.
                // Hence lock2 must be held by the selector, so we wake it up.
                selector_.wakeup();

                // should be able to grab the lock now
                lock2_.lock();
            }
        } finally {
            lock1_.unlock();
        }

        awoken_ = true;
        lock2_.unlock();
    }
}
