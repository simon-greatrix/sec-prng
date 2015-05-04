package prng.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;

/**
 * A receiver and sender of data.
 *
 * @author Simon Greatrix
 *
 */
abstract class Communicator implements Runnable {
    /**
     * States this communicator can be in
     */
    enum State {
        /** Communicator has just been created */
        New,

        /** Communicator is ready to read a message */
        Read,

        /** Communicator is writing a message */
        Write,

        /** Communicator is writing a final message */
        Final_Write,

        /** Communicator is closed */
        Close
    }

    /** Boss for this receiver */
    protected final Boss boss_;

    /** Socket this reads from */
    protected SocketChannel channel_;

    /** Lock for channel status changes */
    protected final Object channelLock_ = new Object();

    /** Any asynchronous error */
    private ServerException error_ = null;

    /** The queue used to transfer raw data in to the box */
    private final Queue<ByteBuffer> input_ = new LinkedList<ByteBuffer>();

    /** Is this reciever's channel closed? */
    private boolean isClosed_ = false;

    /** Is this currently running? */
    private boolean isRunning_ = false;

    /** The queue used to transfer data out to the socket */
    private final Queue<ByteBuffer> output_ = new LinkedList<ByteBuffer>();

    /** The reader for messages */
    protected final MessageReader reader_ = new MessageReader();

    /** The selection key for this receiver's channel */
    protected SelectionKey selectionKey_ = null;

    /** Current state of this */
    protected State state_ = State.New;

    /** Writer for messages */
    protected final MessageWriter writer_ = new MessageWriter();


    /**
     * New receiver which monitors the specified channel
     *
     * @param boss
     *            the boss of this receiver
     * @param channel
     *            the socket to monitor
     */
    Communicator(Boss boss, SocketChannel channel) {
        boss_ = boss;
        channel_ = channel;
    }


    /**
     * Wait until the channel is ready for operations, or closed.
     *
     * @return true on success, false if channel was closed
     * @throws InterruptedException
     */
    public boolean waitForReady() throws InterruptedException {
        synchronized (channelLock_) {
            while( state_ != State.Read && state_ != State.Close ) {
                channelLock_.wait();
            }

            return state_ == State.Read;
        }
    }


    /**
     * Get the channel this receiver monitors
     *
     * @return the channel
     */
    SocketChannel channel() {
        return channel_;
    }


    /**
     * Close this communicator. The actual close is handled by the boss.
     */
    void close() {
        // any state can go to a close
        boolean needUpdate;
        synchronized (channelLock_) {
            state_ = State.Close;

            // if channel is null, already closed
            needUpdate = (channel_ != null);
        }
        if ( needUpdate ) boss_.update(this);
    }


    /**
     * Close the channel of this receiver
     */
    private void processClose() {
        synchronized (channelLock_) {
            isClosed_ = true;

            // don't select this again
            if ( selectionKey_ != null ) selectionKey_.cancel();

            // close the channel
            if ( channel_ != null ) {
                try {
                    channel_.close();
                } catch (IOException ioe) {
                    ServerMain.LOG.warn("CACHE_NET_CLOSE_ERROR",ioe);
                }
            }
            state_ = State.Close;
            output_.clear();
            channel_ = null;

            channelLock_.notifyAll();
        }
    }


    /**
     * Process this communicator. The boss calls this when it knows there is
     * something to be done.
     *
     * @param exec
     *            an Executor for handling further work
     */
    void process(ExecutorService exec) {
        State state;
        synchronized (channelLock_) {
            state = state_;
        }
        switch (state) {
        case New:
            throw new IllegalStateException();
        case Read:
            processRead(exec);
            return;
        case Write:
        case Final_Write:
            processWrite();
            return;
        case Close:
            return;
        }
    }


    /**
     * Process data on the channel
     *
     * @param exec
     *            an Executor to pass jobs on to
     */
    private void processRead(ExecutorService exec) {
        try {
            while( true ) {
                ByteBuffer buf = Buffers.reserve();
                int r = -1;
                synchronized (channelLock_ ) {
                    if ( !isClosed_ ) r = channel_.read(buf);
                }

                // check for EOF
                if ( r == -1 ) {
                    Buffers.release(buf);
                    close();
                    return;
                }

                // if we got a buffer submit it for processing
                buf.flip();
                if ( buf.hasRemaining() ) {
                    synchronized (input_) {
                        input_.offer(buf);
                        if ( !isRunning_ ) {
                            isRunning_ = true;
                            exec.submit(this);
                        }
                    }
                } else {
                    // no data, so done
                    Buffers.release(buf);
                    break;
                }
            }
        } catch (IOException ioe) {
            ServerException bme = new ServerException("CACHE_MESSAGE_READ_FAILED", ioe);
            synchronized (input_) {
                error_ = bme;
                input_.clear();
            }

            processClose();
        }
    }


    /**
     * Process a message received by this receiver.
     *
     * @param msg
     *            the message to process
     */
    abstract protected void process(Message msg);


    /**
     * Update this handler with a selector. This must only be invoked by the
     * thread that owns the selector!
     */
    void updateNow() {
        State state;
        synchronized (channelLock_) {
            state = state_;
        }
        switch (state) {
        case New: {
            synchronized (channelLock_) {
                SelectionKey key;
                Selector selector = boss_.getSelector();
                try {
                    key = channel_.register(selector, SelectionKey.OP_READ);
                } catch (ClosedChannelException e) {
                    ServerMain.LOG.error("CONNECTION_CLOSED_BEFORE_USED", e);

                    // mark this as closed
                    isClosed_ = true;
                    selectionKey_ = null;
                    channel_ = null;
                    state_ = State.Close;
                    channelLock_.notifyAll();

                    return;
                }
                key.attach(this);
                selectionKey_ = key;
                state_ = State.Read;
                channelLock_.notifyAll();
            }
            break;
        }
        case Read:
            selectionKey_.interestOps(SelectionKey.OP_READ);
            break;
        case Write:
            selectionKey_.interestOps(SelectionKey.OP_WRITE);
            break;
        case Final_Write:
            selectionKey_.interestOps(SelectionKey.OP_WRITE);
            break;
        case Close:
            processClose();
            break;
        }
    }


    @Override
    public void run() {
        ByteBuffer buf = null;
        try {
            while( true ) {
                // do we need a new buffer?
                if ( buf == null ) {
                    synchronized (input_) {
                        buf = input_.poll();

                        // if no more buffers, we are done
                        if ( buf == null ) {
                            if ( error_ != null ) process(new Message(error_));
                            isRunning_ = false;
                            return;
                        }
                    }
                }

                // read the message
                Message msg = null;
                try {
                    msg = reader_.read(buf);
                } catch (ServerException e) {
                    ServerMain.LOG.error("ERROR_READING_CACHE_MESSAGE", e);
                    msg = new Message(e);
                }

                if ( !buf.hasRemaining() ) {
                    Buffers.release(buf);
                    buf = null;
                }

                if ( msg != null ) {
                    process(msg);
                }
            }
        } catch (RuntimeException e) {
            // on runtime exception, ensure isRunning is set to false
            if ( isRunning_ ) isRunning_ = false;
            throw e;
        } catch (Error e) {
            // on error, ensure isRunning is set to false
            if ( isRunning_ ) isRunning_ = false;
            throw e;
        }
    }


    /**
     * Write a message to the channel
     */
    private void processWrite() {
        synchronized (output_) {
            while( true ) {
                // any queued output?
                ByteBuffer buf = output_.peek();

                if ( buf == null ) break;

                // write some of the queued output
                try {
                    synchronized (channelLock_) {
                        if ( isClosed_ ) {
                            // can't write, so dispose
                            buf.clear();
                        } else {
                            // not closed, so write
                            channel_.write(buf);
                        }
                    }
                } catch (IOException ioe) {
                    process(new Message(new ServerException("IO_EXCEPTION",ioe)));
                    return;
                }

                // if this buffer is fully written, remove it from queue
                if ( !buf.hasRemaining() ) {
                    output_.poll();
                } else {
                    // socket buffer must be full
                    break;
                }
            }

            if ( output_.isEmpty() ) {
                // don't go back on a close
                synchronized (channelLock_) {
                    state_ = (state_ == State.Write) ? State.Read : State.Close;
                    channelLock_.notifyAll();
                    updateNow();
                }
            }
        }
    }


    /**
     * Write a message to this channel
     *
     * @param msg
     *            the message to write
     * @param isFinal
     *            true if this is the last message
     * @throws BMException
     */
    void write(IMessage msg, boolean isFinal) throws ServerException {
        ByteBuffer buf = writer_.write(msg);
        write(buf, isFinal);
    }


    /**
     * Write data to this channel
     *
     * @param buf
     *            the data to write
     * @param isFinal
     *            true if this is the last message
     */
    void write(ByteBuffer buf, boolean isFinal) {
        boolean needUpdate = isFinal;

        synchronized (output_) {
            // are writes pending?
            if ( output_.isEmpty() ) {
                try {
                    // no pending writes so write now
                    synchronized (channelLock_) {
                        if ( channel_ != null ) {
                            channel_.write(buf);
                        } else {
                            buf.clear();
                            process(new Message(new ServerException(
                                    "NET_CACHE_CHANNEL_CLOSED")));
                        }
                    }
                } catch (IOException ioe) {
                    process(new Message(new ServerException("IO_EXCEPTION", ioe)));
                    return;
                }
            }

            // do we still have stuff to write?
            int rem = buf.remaining();
            if ( rem > 0 ) {
                // add message to out-bound queue
                ByteBuffer buf2 = ByteBuffer.allocate(rem);
                buf2.put(buf).flip();
                output_.offer(buf2);

                // we will need to update the boss that there is queued data
                needUpdate = true;
            }
        }

        // update the channel status
        if ( needUpdate ) {
            synchronized (channelLock_) {
                // don't go back on a close
                if ( state_ != State.Close )
                    state_ = isFinal ? State.Final_Write : State.Write;
                channelLock_.notifyAll();
            }

            // now all sync locks released, we can update the boss safely
            boss_.update(this);
        }
    }
}
