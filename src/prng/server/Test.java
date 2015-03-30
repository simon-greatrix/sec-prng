package prng.server;

import java.io.DataInputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Test implements Runnable {
    Socket sock_;
    
    public Test(Socket sock) {
        sock_ = sock;
    }
    
    public void run() {
        try {
            runImpl();
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
    
    public void runImpl() throws Exception {
        InputStream in = sock_.getInputStream();
        DataInputStream din = new DataInputStream(in);
        while(true) {
            
        }
    }
    
    public static void main(String[] args) throws Exception {
        ServerSocket server = new ServerSocket(17071);
        while( true ) {
            Socket sock = server.accept();
            Test test = new Test(sock);
            Thread thread = new Thread(test,"RECV:"+sock.getRemoteSocketAddress());
            thread.start();
        }
    }
}
