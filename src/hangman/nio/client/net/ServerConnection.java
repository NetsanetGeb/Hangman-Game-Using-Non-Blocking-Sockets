
package hangman.nio.client.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;
import hangman.nio.client.view.Client;


public class ServerConnection implements Runnable {
 private final String host;
    private final int port;
    private final Client player;
    private boolean receivedWord;
    private SocketChannel socketChannel;
    private Selector selector;
    private Iterator <SelectionKey> iterator;
    public SelectionKey key;
    private volatile boolean timeToSend = false;
    private final ByteBuffer receivedMsg = ByteBuffer.allocateDirect(8192);
    private final ByteBuffer msgToDeliver = ByteBuffer.allocateDirect(8192);
    private volatile boolean connected;
    private final Queue<ByteBuffer> messageQueue = new ArrayDeque<>();
   

    public ServerConnection(Client player,String host, int port){
         this.host= host;
         this.port= port;
         this.player= player;
     }
    
    public void initial () throws Exception {
               socketChannel = SocketChannel.open();
               socketChannel.configureBlocking(false);
               socketChannel.connect(new InetSocketAddress(host, port));
               connected = true;	
	} 
    
    private void initialSelector() throws IOException {
        selector = Selector.open();
        socketChannel.register(selector, SelectionKey.OP_CONNECT);
    }
    
    @Override
    public void run() {
        try {
            initial ();
            initialSelector();
		while(true) {
                         if (timeToSend) {
                             socketChannel.keyFor(selector).interestOps(SelectionKey.OP_WRITE);
                             timeToSend = false;
                            }
                        selector.select ( );
                        iterator = selector.selectedKeys ().iterator ();
                        while(iterator.hasNext()){
                                key = (SelectionKey) iterator.next();
                                iterator.remove();
                                if (!key.isValid()) continue;
                                if(key.isConnectable()){
                                        Connection(key);
                                } 
                                if(key.isReadable()){
                                        receive(key);
                                } 
                                if(key.isWritable()){
                                        send(key);
                                } 
                        } 
                } 
	} 
        catch (Exception e) {
            System.err.println("Client lost connection with server");
        }
    }
        

    private void Connection(SelectionKey key) throws IOException {
            socketChannel = (SocketChannel) key.channel();
            if (socketChannel.isConnectionPending()){
                socketChannel.finishConnect();
                System.out.println("Client connected to server");
            } 
           // socketChannel.register(selector,SelectionKey.OP_WRITE);
            key.interestOps( SelectionKey.OP_WRITE);
    }
    
    private void receive(SelectionKey key) throws IOException {
         try
           {
            System.out.println("Message Received");
            receivedMsg.clear();
            int numOfReadBytes = socketChannel.read(receivedMsg);
            if (numOfReadBytes == -1) {
                throw new IOException("Failed to read message sent by server");
            }
            String selectedWord = extractMsgFromBuffer();
            player.connected();
            player.updateGameGui(selectedWord);
           // key.interestOps(SelectionKey.OP_WRITE);
        }catch(Exception e){
            System.out.println("Nothing is received");
           e.printStackTrace();
        }
    }
  
    private void send(SelectionKey key) throws IOException, InterruptedException {
         try{
              ByteBuffer msg;
              synchronized (messageQueue) {
                  while ((msg = messageQueue.peek()) != null) {
                        socketChannel.write(msg);
                        if (msg.hasRemaining()) return; // Failed to send the message
                        messageQueue.remove();
                   }
               }
              key.interestOps(SelectionKey.OP_READ);
                     
           /* socketChannel = ( SocketChannel ) key.channel ( );
            byte[] toServer = strings.take().getBytes();
            msgToDeliver.clear();
            msgToDeliver = ByteBuffer.wrap(toServer);
            msgToDeliver.flip();
            socketChannel.write(msgToDeliver);
            key.interestOps(SelectionKey.OP_READ);*/
         }catch(Exception e){
              System.out.println(" player can't send guessed word to server");
         } 
     }
    
  
  private String extractMsgFromBuffer() {
         receivedMsg.flip();
         byte[] bytes = new byte[receivedMsg.remaining()];
         receivedMsg.get(bytes);
         return new String(bytes);
  }
  
  private void doDisconnect() throws IOException {
        socketChannel.close();
        socketChannel.keyFor(selector).cancel();
    }
  
   public static ByteBuffer createMessage(String view) {
         return ByteBuffer.wrap(view.getBytes());
    }
    
   public void addMsg(String text) {
         ByteBuffer msg = createMessage(text);
         System.out.println("Sending: "+text);
         synchronized (messageQueue) {
              messageQueue.add(msg);
          }
        timeToSend = true;
        selector.wakeup();
    }
    
}

