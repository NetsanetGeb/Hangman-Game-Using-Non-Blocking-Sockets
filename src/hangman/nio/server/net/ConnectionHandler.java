
package hangman.nio.server.net;

import java.io.IOException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import hangman.nio.server.model.Dictionary;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

public class ConnectionHandler implements Runnable {
    
    private final Server server;
    private final SocketChannel socketChannel;
    private SelectionKey channelKey;
    private final ByteBuffer receivedMsg = ByteBuffer.allocateDirect(8192);
    public  ByteBuffer msgToDeliver = ByteBuffer.allocateDirect(8192);
     private final Queue<ByteBuffer> messageQueue = new ArrayDeque<>();
    private static final Random rand = new Random();
    Dictionary dict = new Dictionary();
    private  int scoreCounter = 0;
    private int attemptCounter;
    private  Character s;
    private  String enteredString;
    private  String selectedWord;
    private  String viewAtFirst;
    char[] secretWord;
    
    Charset charset = Charset.forName("UTF-8");
    
    public ConnectionHandler(Server server, SocketChannel socketChannel) throws IOException {
        this.server = server;
        this.socketChannel = socketChannel;
       
    }
    
    
    @Override
    public void run(){
        try {
            process(enteredString);
        } catch (IOException ex) {
            Logger.getLogger(ConnectionHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    
     private void sendMsg(ByteBuffer msg) throws IOException {
        try{
          socketChannel.write(msg);
          if (msg.hasRemaining()) throw new IOException("The message could not be sent!");
          System.out.println("message sent");
        }catch(Exception e){
          e.printStackTrace();
         }
  }
    
    
    void receiveMsg() throws IOException {
        receivedMsg.clear();
        int numOfReadBytes;
        numOfReadBytes = socketChannel.read(receivedMsg);
        if (numOfReadBytes == -1) {
            throw new IOException("Client has closed connection.");
        }
        String receivedString = extractMessageFromBuffer();
        process(receivedString);
        CompletableFuture.runAsync(this);
    }
      
    public static ByteBuffer createMessage(String view) {
         return ByteBuffer.wrap(view.getBytes());
    }
    
   public void addMsg(String view) {
         ByteBuffer msg = createMessage(view);
         //System.out.println("Sending"+view);
         synchronized (messageQueue) {
              messageQueue.add(msg);
          }
        server.addPendingMsg(channelKey);
        server.wakeup();
    }
    
     void sendMessages() throws IOException {
         ByteBuffer msg;
         synchronized (messageQueue) {
            while ((msg = messageQueue.peek()) != null) {
               sendMsg(msg);
               messageQueue.poll();
            }
         }
     }

     
    private String extractMessageFromBuffer() {
        receivedMsg.flip();
        byte[] bytes = new byte[receivedMsg.remaining()];
        receivedMsg.get(bytes);
        return new String(bytes);
    }

    
   public void registerKey(SelectionKey channelKey) {
         this.channelKey = channelKey;
   }
   
   public void disconnectClient() {
       try {
            socketChannel.close();
       } catch (IOException e) {
           System.err.println("Something went wrong when trying to disconnect a client...");
       }
   }
   
   public void firstView() throws IOException{
        selectedWord= dict.getDictionaryLine(rand.nextInt(10000));
        System.out.println(selectedWord);
        secretWord = new char[selectedWord.length()];
        attemptCounter = 6;
        System.out.println(selectedWord);
        for (int i = 0; i < selectedWord.length(); i++) {
            secretWord[i] = '_';
        }
        viewAtFirst = new String(secretWord).concat(Integer.toString(attemptCounter)).concat(getScoreNotation(scoreCounter));
        addMsg(viewAtFirst);
}
    
    public void process(String enteredString) throws IOException{
          boolean charFound = false;
          boolean first=true;
              while (attemptCounter > 0 && first) {
                   String view;
// if Input is character
                   first=false;
                    if (enteredString.length() ==1) {
                            s = enteredString.charAt(0);
                            int index = 0;
                            while (index < selectedWord.length()) {
                                if (characterCompare(s, selectedWord.charAt(index)) == true) {
                                    if(secretWord[index]!=s)//handles repeated input of the same character
                                        charFound = true;
                                         System.out.println("character found");
                                    secretWord[index] = s;
                                    index++;

                                    continue;
                                }
                                index++;
                            }
                            if (!charFound) {
                                attemptCounter--;
                                System.out.println("character not found");
                            }
                            charFound = false;
                            //  if the guess of a word is corret , Increment score and select new word to Guess
                            if (String.valueOf(secretWord).compareToIgnoreCase(selectedWord) == 0) {
                                System.out.println("Guessed correctly using char");
                                scoreCounter++;
                                attemptCounter = 0;
                                selectedWord = wordSelection();
                                viewAtFirst = new String(wordSelection(selectedWord)).concat(Integer.toString(attemptCounter)).concat(getScoreNotation(scoreCounter));
                                // addMsg(viewAtFirst);
                                firstView();
                            } else if (attemptCounter == 0) {
                          //if the guess of a word is incorret , Decrement score and select new word to Guess
                                     System.out.println("Guessed Failed using Char");
                                    scoreCounter--;
                                    selectedWord = wordSelection();
                                   viewAtFirst = new String(wordSelection(selectedWord)).concat(Integer.toString(attemptCounter)).concat(getScoreNotation(scoreCounter));
                                   firstView();
                            } else {
                                System.out.println("continue guessing");
                                view = new String(secretWord).concat(Integer.toString(attemptCounter)).concat(getScoreNotation(scoreCounter));
                                addMsg(view);
                            }
                            
                            
                    } else if(enteredString.length() ==0){
                         System.out.println("Forget to Insert word");
                         attemptCounter = 0;
                         selectedWord = wordSelection();
                         viewAtFirst = new String(wordSelection(selectedWord)).concat(Integer.toString(attemptCounter)).concat(getScoreNotation(scoreCounter));
                         firstView();
                    
                    }else{
//if input is a word
                        if (enteredString.compareToIgnoreCase(selectedWord) == 0) {
                             //if the guess of a word is corret , Increment score and select new word to Guess
                            System.out.println("Guessed correctly");
                            scoreCounter++;
                            attemptCounter = 0;
                            selectedWord = wordSelection();
                            viewAtFirst = new String(wordSelection(selectedWord)).concat(Integer.toString(attemptCounter)).concat(getScoreNotation(scoreCounter));
                            //addMsg(viewAtFirst);
                            firstView();

                            break;
                        } else {
                            //if the guess of a word is Incorret , Decrement score and select new word to Guess                           
                            attemptCounter--;
                            if (attemptCounter == 0) {
                                scoreCounter--;
                                System.out.println("Guessed failed");
                            }
                            System.out.println("Guessed failed failed");
                            view = new String(secretWord).concat(Integer.toString(attemptCounter)).concat(getScoreNotation(scoreCounter));
                            addMsg(view);
                        }
                    }
                }
    }
    
     private String getScoreNotation(int x) {
        if (x == 0) {
            return "00";
        }
        if (x > 0) {
            return "+" + x;
        } else {
            return "-" + x * -1;
        }
    }
     
    private static boolean characterCompare(char c1, char c2) {
        return Character.toString(c1).compareToIgnoreCase(Character.toString(c2)) == 0;
    }
    
    
     private String wordSelection() throws IOException {
        return dict.getDictionaryLine(rand.nextInt(10000));
    }
     
    private char[] wordSelection(String str) throws IOException {
        secretWord = new char[str.length()];
        for (int i = 0; i < str.length(); i++) {
            secretWord[i] = '_';
        }
        return secretWord;
    }
    
    
}
