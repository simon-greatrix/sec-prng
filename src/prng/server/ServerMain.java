package prng.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerMain {
    /** Logger for the server */
    static final Logger LOG = LoggerFactory.getLogger(ServerMain.class);
    
    /** Is the server shutdown? */
    private boolean shutdown_ = false;
    
    protected void runServer() throws ServerException {

        // configure the server socket
        Selector selector;
        ServerSocketChannel server;
        CacheBoss boss;
        try {
            selector = Selector.open();
            server = ServerSocketChannel.open();
            InetSocketAddress sa = getSocketAddress();
            server.socket().bind(sa);
            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Listening on " + sa);
            boss = new CacheBoss(this);
        } catch (IOException ioe) {
            LOG.error("Initialisation failed", ioe);
            System.err.print("\n\n");
            ioe.printStackTrace(System.err);
            System.err.println("\n\nFailed to start. Shutting down.");
            return;
        }

        // Start the boss thread that will run the handlers
        boss.start();

        // handle new exceptions at high priority
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

        while( !(shutdown_  || Thread.interrupted()) ) {
            // wait at most a second so we spot shutdowns.
            try {
                int ready = selector.select(1000);
                if ( ready == 0 ) continue;
            } catch (IOException ioe) {
                LOG.error("ERROR_SELECTING_CONNECTION", ioe);
                continue;
            }

            Set<SelectionKey> keys = selector.selectedKeys();

            Iterator<SelectionKey> iter = keys.iterator();
            while( iter.hasNext() ) {
                iter.next();
                try {
                    // pass new channel to boss to set up
                    SocketChannel chan = server.accept();
                    boss.accept(chan);
                } catch (IOException ioe) {
                    LOG.error("ERROR_ACCEPTING_CONNECTION", ioe);
                }
                iter.remove();
            }
        }

        // close the server socket
        try {
            server.close();
        } catch (IOException ioe) {
            LOG.warn("SOCKET_CLOSE_EXCEPTION",ioe);
        }

        boss.shutdown();
    }

    private InetSocketAddress getSocketAddress() {
        // TODO Auto-generated method stub
        return null;
    }

}
