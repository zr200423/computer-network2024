//package TCPserver;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPServer {
    private ServerSocket serverSocket;
    private ExecutorService executorService;//线程池

    public TCPServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        executorService = Executors.newCachedThreadPool();
    }

    public void start() {
        System.out.println("服务器启动！");
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                executorService.execute(new ClientHandler(socket));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        public void run() {
            try (Socket socket = this.clientSocket;
                 DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                 //读入initialization报文
            	 byte[] initializationBytes = new byte[6];
                 in.readFully(initializationBytes);
                 int type = (initializationBytes[0] << 8) | (initializationBytes[1] & 0xFF); // 解析 Type
                 if (type == 1) {
                	 int blockCount = (initializationBytes[2] << 24) | (initializationBytes[3] << 16)
                    		 | (initializationBytes[4] << 8) | (initializationBytes[5] & 0xFF); // 解析 N
                    System.out.println("收到Initialization报文，请求块数：" + blockCount);
                    
                    out.writeShort(2); //发送agree报文
                    out.flush();

                    for (int i = 0; i < blockCount; i++) {
                    	//读入reverseRequest报文
                        byte[] reverseRequestBytes = new byte[1006];
                        in.readFully(reverseRequestBytes);
                        type = (reverseRequestBytes[0] << 8) | (reverseRequestBytes[1] & 0xFF); // 解析 Type
                        if (type != 3) { //判断是否收到reverseRequest报文
                           break;
                        }
                        int length = (reverseRequestBytes[2] << 24) | (reverseRequestBytes[3] << 16) |
                        		(reverseRequestBytes[4] << 8) | (reverseRequestBytes[5] & 0xFF); // 解析 Length
                        String message = new String(reverseRequestBytes, 6, length); // 解析 Data
                        System.out.println("收到ReverseRequest报文，Data：" + message);
                        
                        StringBuilder data = new StringBuilder(message);
                        String reversed = data.reverse().toString();//反转文本

                        //发送reverseAnswer报文
                        byte[] reverseAnswerBytes = createReverseAnswer(4, reversed);
                        out.write(reverseAnswerBytes);
                        out.flush();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    //构造reverseAnswer报文
    private static byte[] createReverseAnswer(int Type,String message) {
        byte[] data = new byte[1006];
        data[0] = (byte) ((Type >> 8) & 0xFF);
        data[1] = (byte) (Type & 0xFF); // 2字节的Type
        byte[] messageBytes = message.getBytes();
        int length = Math.min(messageBytes.length, 1000);
        data[2] = (byte) ((length >> 24) & 0xFF); // 4字节的Length
        data[3] = (byte) ((length >> 16) & 0xFF);
        data[4] = (byte) ((length >> 8) & 0xFF);
        data[5] = (byte) (length & 0xFF);
        System.arraycopy(messageBytes, 0, data, 6, length); // 1000字节message
        return data;
    }

    public static void main(String[] args) {
        try {
            new TCPServer(2428).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
