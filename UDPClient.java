//package UDPClient;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.time.LocalTime;
import java.time.Duration;

public class UDPClient {
    private DatagramSocket socket;
    private String serverIP;
    private int serverPort;
    private boolean connectionEstablished;
    private int sequenceNumber = 0;
    private List<Long> rttList;
    private long totalResponseTime = 0; // 累计响应时间
//    private long firstTimestamp = 0;
//    private long lastTimestamp = 0;
    LocalTime startime=LocalTime.parse("00:00:00");
    LocalTime endtime=LocalTime.parse("00:00:00");
    
    public UDPClient(String serverIP, int serverPort) throws SocketException {
        socket = new DatagramSocket();
        this.serverIP = serverIP;
        this.serverPort = serverPort;
        connectionEstablished = true;
        this.rttList = new ArrayList<>();
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

        // 输出客户端连接信息
        System.out.println("客户端：连接成功,Server IP:" + serverIP + ",Server Port:" + serverPort);
                 
       
        long rtt=0;
        String timeStamp=null;
        int num=0;//记录成功接收到的报文条数
        int sendPacket=0;//记录发送了多少次报文
        DatagramPacket responsePacket=null;
        while (sequenceNumber < 12 && connectionEstablished) {
        	//输入想要发送消息
            System.out.print("-> ");
            String request = scanner.next();
         
            sequenceNumber++;//记录发送消息的序号，从1开始

            //输入CLOSE断开连接
            if (request.equalsIgnoreCase("CLOSE")) {
                connectionEstablished = false;
                sequenceNumber--;
                System.out.println("服务器: 连接已关闭。");
                byte[] data = createPacketData(sequenceNumber, request);
                DatagramPacket packet = new DatagramPacket(data, data.length,  InetAddress.getByName(serverIP), serverPort);
                socket.send(packet);
                break;
            }
            boolean receivedResponse = false;//判断是否接收成功
            int retries = 0;//重传次数
            while (!receivedResponse && retries < 3) { // 最多重传2次
            	//发送报文
            	 byte[] data = createPacketData(sequenceNumber, request);
                 DatagramPacket packet = new DatagramPacket(data, data.length,  InetAddress.getByName(serverIP), serverPort);
                 socket.send(packet);
                 
                 sendPacket++;//记录发送了多少次报文，用于计算丢包率
                 
                 long startTime = System.nanoTime();//记录发送报文的时间
                 
                socket.setSoTimeout(100); // 设置超时时间为100ms
                try {
                	//接收报文
                    responsePacket = new DatagramPacket(new byte[4096], 4096);
                    socket.receive(responsePacket);
                    
                    byte[] data2 = responsePacket.getData();
                    byte[] timeBytes = new byte[8];
                    System.arraycopy(data2, 203 - 8, timeBytes, 0, 8);
                    timeStamp = new String(timeBytes, StandardCharsets.UTF_8).trim();
                    num++;
                    receivedResponse = true;
                    if(startime==LocalTime.parse("00:00:00"))
                    {
                    	String[] parts = timeStamp.split(":");
                    	startime = LocalTime.of(Integer.parseInt(parts[0]),
                    			Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    }
                    String[] parts = timeStamp.split(":");
                    endtime = LocalTime.of(Integer.parseInt(parts[0]), 
                    		Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));

                    if(retries==0)
                    {
                    	 rtt = System.nanoTime() - startTime;
                    	 rtt/=100000;
                    	// 存储RTT
                         rttList.add(rtt);
                        InetAddress address = responsePacket.getAddress();
                        int port = responsePacket.getPort();
                        System.out.println("客户端: 接收成功,Server IP:" + address.getHostAddress() + ",Server Port:" + port+","
                        		+ "序号:"+sequenceNumber+",时间:"+timeStamp+",RTT:"+rtt);  
                    }
                    
                } catch (IOException e) {
                    System.out.println("序号:"+sequenceNumber+",客户端:超时,请求重传。");
                    retries++;
                   // receivedResponse = false;
                }
            }
            if (!receivedResponse&&retries>=3) {
                System.out.println("序号:"+sequenceNumber+",客户端: 请求重传失败。");
            } 
            else if(receivedResponse&&retries>0){
            	 InetAddress address = responsePacket.getAddress();
                 int port = responsePacket.getPort();
                 System.out.println("客户端: 重传成功,Server IP:" + address.getHostAddress() + ",Server Port:" + port+",序号:"+sequenceNumber
                 		+",时间:"+timeStamp);  
            }
        }
        printSummary(num,sendPacket) ;
    }
    private long bytesToLong(byte[] bytes) {
        long value = 0;
        for (int i = 0; i < bytes.length; i++) {
            value = (value << 8) | (bytes[i] & 0xFF);
        }
        return value;
    }
	private byte[] createPacketData(int sequenceNumber, String message) {
        byte[] data = new byte[203];
        data[0] = (byte) ((sequenceNumber >> 8) & 0xFF);
        data[1] = (byte) (sequenceNumber & 0xFF);//2字节的序号
        data[2] = 2; // 版本号固定为2
        byte[] messageBytes = message.getBytes();
        System.arraycopy(messageBytes, 0, data, 3, Math.min(messageBytes.length, 200));//200字节message
        return data;
    }

    private double calculateStandardDeviation(List<Long> rttList, int count, double mean) {
        double variance = 0.0;
        for (long rtt : rttList) {
            variance += Math.pow(rtt - mean, 2);
        }
        variance /= count;
        return Math.sqrt(variance);
    }

    public void printSummary(int num,int sendPacket) {
        int receivedPackets = num;
        double lossRate = (double) (sendPacket - num) / sendPacket * 100;
        System.out.println("接收到的UDP包数目: " + num);
        System.out.println("丢包率: " + lossRate + "%");
        if(!rttList.isEmpty())
        {
        	 long maxRTT = Collections.max(rttList);
             long minRTT = Collections.min(rttList);
             double meanRTT = rttList.stream().mapToLong(Long::longValue).average().orElse(0);
             double stdDevRTT = calculateStandardDeviation(rttList, receivedPackets, meanRTT);
             System.out.println("最大RTT: " + maxRTT + " ms");
             System.out.println("最小RTT: " + minRTT + " ms");
             System.out.println("平均RTT: " + meanRTT + " ms");
             System.out.println("RTT的标准差: " + stdDevRTT + " ms");
        }
        // 计算时间差
        Duration duration = Duration.between(startime, endtime);

        // 获取时间差的毫秒数
        long milliseconds = duration.toMillis();
        //long overallResponseTime = lastTimestamp - firstTimestamp;
        System.out.println("服务端的整体响应时间: " + milliseconds + " ms");
    }


    public static void main(String[] args) throws IOException {
        UDPClient client = new UDPClient("127.0.0.1", 9090);
        client.start();
    }
}
