
package hangman.nio.server.net;

import java.io.IOException;
import com.sun.istack.internal.NotNull;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.InvalidParameterException;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;


public class Server {
    
    private static final int LINGER_TIME = 5000;
    private final int portNo = 8080;
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;
    private final ByteBuffer receivedMsg = ByteBuffer.allocateDirect(8192);
    private final  ByteBuffer msgToDeliver = ByteBuffer.allocateDirect(8192);
    private final Queue<SelectionKey> pendingWrite = new ArrayDeque<>();
    
    private void operate() {
        
        try {
            initialSelector();
            listeningSocketChannel();
            while(true){
                    while (!pendingWrite.isEmpty()) pendingWrite.poll().interestOps(SelectionKey.OP_WRITE);
                    selector.select();
                    Iterator<SelectionKey> iterate = selector.selectedKeys().iterator();
                    while (iterate.hasNext()) {
                            SelectionKey key = iterate.next();
                            iterate.remove();
                            if (!key.isValid()) {
                                continue;
                            }
                            if (key.isAcceptable()) {
                                     acceptConnection(key);
                            } else if (key.isReadable()) {
                                     receive(key);
                                    // System.out.println("received");
                            } else if (key.isWritable()) {
                                    send(key);
                            }
                       }
             }
       } catch (IOException e) {
              System.err.println("Server failed.");
              e.printStackTrace();
       } catch (Exception e) {
              System.err.println(e.getMessage());
              e.printStackTrace();
       }
    }
    
     private void initialSelector() throws IOException {
        selector = Selector.open();
    }

    private void listeningSocketChannel() throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(portNo));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
         System.out.println(" Server Running");
    }
    
    public static void main(String [] args) throws IOException{
        
         Server server = new Server();
         server.operate();
        
    }
    
    private void acceptConnection(SelectionKey key) throws IOException {
        System.out.println("A client wants to connect!");
        serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        ConnectionHandler handler = new ConnectionHandler(this, socketChannel);
        handler.registerKey(socketChannel.register(selector, SelectionKey.OP_READ, handler));
        handler.firstView();
         socketChannel.setOption(StandardSocketOptions.SO_LINGER, LINGER_TIME);
        
    }
    
    private void receive(SelectionKey key) throws IOException {
        
            ConnectionHandler handler = (ConnectionHandler) key.attachment();
            try {
                handler.receiveMsg();
            } catch (IOException clientClosedConn) {
               System.out.println("A client closed their connection!");
               removeClient(key);
           }
    }

    private void send(SelectionKey key) throws IOException {
      
         try{
              ConnectionHandler handler = (ConnectionHandler) key.attachment();
              handler.sendMessages();
              key.interestOps(SelectionKey.OP_READ);
         }
        catch (Exception e) {
                System.err.println("could not send message to server");
                e.printStackTrace();
        }
     }
       
    private void removeClient(SelectionKey clientKey) throws IOException {
        ConnectionHandler handler = (ConnectionHandler) clientKey.attachment();
        handler.disconnectClient();
        clientKey.cancel(); 
    }
    
    public void addPendingMsg(@NotNull SelectionKey channelKey) throws InvalidParameterException {
          if (channelKey == null) throw new InvalidParameterException("ChannelKey must be defined!");
          if (!channelKey.isValid() || !(channelKey.channel() instanceof SocketChannel))
              throw new InvalidParameterException("The channel key is invalid!");
 
          pendingWrite.add(channelKey);
    }
    
    public void wakeup() {
      selector.wakeup();
    }
 }

