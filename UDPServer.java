//package UDPServer;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;

public class UDPServer {
    private DatagramSocket socket;

    public UDPServer(int port) throws SocketException {
        socket = new DatagramSocket(port);
    }
    public void start() throws IOException {
        System.out.println("服务器启动！");
        while (true) {
            // 读取请求并解析
            DatagramPacket requestPacket = new DatagramPacket(new byte[4096], 4096);
            socket.receive(requestPacket);
            byte[] data = requestPacket.getData();
            int sequenceNumber = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
            int version = data[2];
          
            String request = new String(data, 3, requestPacket.getLength() - 3);
            // 模拟TCP连接释放过程
            if (request.trim().equalsIgnoreCase("CLOSE")) {
                System.out.println("服务器: 连接关闭。");
                socket.close(); // 关闭连接
                break;
            }
            else
            {
            	if (version != 2) {
                    System.out.println("无效的版本号");
                    continue;
                }
                // 模拟UDP丢包情况
                if (dropPacket()) {
                    System.out.printf("序号：%d，服务器: 模拟丢包。\n",sequenceNumber);
                }
                else
                {
                	// 记录收到消息的时间
                    String receiveTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
                    String response = processRequest(request);
                    byte[] responseData = createPacketData(sequenceNumber, response, receiveTime);
                    DatagramPacket sendPacket = new DatagramPacket(responseData, responseData.length, requestPacket.getAddress(), requestPacket.getPort());
                    socket.send(sendPacket);
                    System.out.printf("[Client IP:%s,Client Port:%d]时间：%s,序号: %d,版本号: %d,请求：%s\n", requestPacket.getAddress().getHostAddress(), requestPacket.getPort(),receiveTime,sequenceNumber,version,request);
                }
               
            }
            }
            
    }

    private boolean dropPacket() {
        return new Random().nextInt(10) > 5; // 50%的概率丢包
    }

    private String processRequest(String request) {
        return request; // 此处应根据实际需求处理请求并返回响应
    }

    private byte[] createPacketData(int sequenceNumber, String response, String receiveTime) {
        byte[] data = new byte[203];
        data[0] = (byte) ((sequenceNumber >> 8) & 0xFF);
        data[1] = (byte) (sequenceNumber & 0xFF);
        data[2] = 2; // 版本号固定为2
        byte[] responseBytes = response.getBytes();
        System.arraycopy(responseBytes, 0, data, 3, Math.min(responseBytes.length, 200));
        // 将时间戳放入报文末尾
        byte[] timeBytes = receiveTime.getBytes();
        System.arraycopy(timeBytes, 0, data, 203 - timeBytes.length, timeBytes.length);
        return data;
    }

    public static void main(String[] args) throws IOException {
        UDPServer server = new UDPServer(9090);
        server.start();
    }
}
