//package TCPclient;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class TCPClient {
    private String serverIP;
    private int serverPort;
    private int Lmin;
    private int Lmax;
    private List<Integer> blockSizes;

    public TCPClient(String serverIP, int serverPort) {
        this.serverIP = serverIP;
        this.serverPort = serverPort;
        this.blockSizes = new ArrayList<>();
    }

    public void start() throws IOException {
        Scanner scanner = new Scanner(System.in);
        // 获取客户端IP和端口号
        System.out.println("Server IP: ");
        String IP = scanner.nextLine();
        System.out.println("Server port: ");
        int Port = scanner.nextInt();

        // 验证IP和端口号是否正确
        if (!IP.equals(serverIP) || Port != serverPort) {
            System.out.println("IP或者Port输入错误，连接失败");
            return;
        }
        else
        {
        	System.out.println("连接成功！");
        }
        //获取Lmin、Lmax
        System.out.println("Lmin: ");
        int Lmin = scanner.nextInt();
        System.out.println("Lmax: ");
        int Lmax = scanner.nextInt();

        try (Socket socket = new Socket(serverIP, serverPort);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            File file = new File("D:\\input.txt");
            long fileSize = file.length();
            long remainingSize = fileSize;
            Random random = new Random();
            //计算块数
            while (remainingSize > 0) {
                int blockSize = Lmin + random.nextInt(Lmax - Lmin + 1);
                if (blockSize > remainingSize) {
                    blockSize = (int) remainingSize;
                }
                blockSizes.add(blockSize);
                remainingSize -= blockSize;
            }
            int blockCount = blockSizes.size();
            //发送initialization报文
            out.write(createInitialization(1,blockCount));
            out.flush();
            //读入agree报文
            int type = in.readShort();
            if (type != 2) { //agree
                throw new IOException("接受agree报文失败");
            }
            System.out.println("收到agree报文");
            
            //从文件中读取分配好的长度的文本
            StringBuilder reversedContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                char[] buffer = new char[Lmax];
                for (int i = 0; i < blockCount; i++) {
                    int blockSize = blockSizes.get(i);
                    int charsRead = reader.read(buffer, 0, blockSize);
                    if (charsRead != blockSize) {
                        throw new IOException("从文件中读取预期的块大小失败");
                    }
                    //发送reverseRequest报文
                    out.write(createReverseRequest(3,blockSize,new String(buffer, 0, blockSize)));
                    out.flush();
                    //读入reverseAnswer报文
                    byte[] reverseAnswerBytes = new byte[1006];
                    in.readFully(reverseAnswerBytes);
                    type = (reverseAnswerBytes[0] << 8) | (reverseAnswerBytes[1] & 0xFF); // 解析 Type
                    if (type != 4) { // 判断是不是reverseAnswer报文
                        break;
                    }
                    int reversedLength = (reverseAnswerBytes[2] << 24) | (reverseAnswerBytes[3] << 16) |
                            (reverseAnswerBytes[4] << 8) | (reverseAnswerBytes[5] & 0xFF); // 解析 Length
                    String reversedString = new String(reverseAnswerBytes, 6, reversedLength);
                    
                    System.out.println((i + 1) + ": " + reversedString);
                    
                    reversedContent.insert(0, reversedString);//将文本插入列表开头
                }
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter("D:\\output.txt"))) {
                writer.write(reversedContent.toString());
            }
        }
    }
    private byte[] createInitialization(int Type, int N) {
        byte[] data = new byte[6];
        data[0] = (byte) ((Type >> 8) & 0xFF);
        data[1] = (byte) (Type & 0xFF);//2字节的Type
        data[2] = (byte) ((N >> 24) & 0xFF); // 4 字节的 N
        data[3] = (byte) ((N >> 16) & 0xFF);
        data[4] = (byte) ((N >> 8) & 0xFF);
        data[5] = (byte) (N & 0xFF);
        return data;
    }
    private byte[] createReverseRequest(int Type, int length,String message) {
        byte[] data = new byte[1006];
        data[0] = (byte) ((Type >> 8) & 0xFF);
        data[1] = (byte) (Type & 0xFF);//2字节的Type
        data[2] = (byte) ((length >> 24) & 0xFF); // 4 字节的 Length
        data[3] = (byte) ((length >> 16) & 0xFF);
        data[4] = (byte) ((length >> 8) & 0xFF);
        data[5] = (byte) (length & 0xFF);
        byte[] messageBytes = message.getBytes();
        System.arraycopy(messageBytes, 0, data, 6, Math.min(messageBytes.length, 1000));//1000字节message
        return data;
    }
    public static void main(String[] args) {
        TCPClient client = new TCPClient("127.0.0.1", 2428);
        try {
            client.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
