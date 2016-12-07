package com.andersenlab.firstServer;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

/**
 * ����� �������. ��������� ���������, �������
 * SocketProcessor �� ������ ���������
 */
public class Server {
    private ServerSocket serverSocket; // ��� ������-�����
    private Thread serverThread; // ������� ���� ��������� ������-������
    private int port; // ���� ������ ������.
    // �������, ��� ��������� ��� SocketProcessor� ��� ��������
    BlockingQueue<SocketProcessor> queue = new LinkedBlockingQueue<SocketProcessor>();

    /**
     * ����������� ������� �������
     * 
     * @param port
     *            ����, ��� ����� ������� �������� ���������.
     * @throws IOException
     *             ���� �� �������� ������� ������-�����, ������� �� ���������,
     *             ������ ������� �� ����� ������
     */
    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port); // ������� ������-�����
        this.port = port; // ��������� ����.
        Client.log.info("Server started");
    }

    /**
     * ������� ���� �������������/�������� ��������.
     */
    void run() {
        serverThread = Thread.currentThread(); // �� ������ ��������� ����
                                               // (����� ����� �� ����
                                               // interrupt())
        while (true) { // ����������� ����, ����...
            Socket s = getNewConn(); // �������� ����� ���������� ���
                                     // ����-���������
            if (serverThread.isInterrupted()) { // ���� ��� ����-����������, ��
                                                // ���� ���� ���� interrupted(),
                // ���� ����������
                break;
            } else if (s != null) { // "������ ���� ������� ������� ������"...
                try {
                    final SocketProcessor processor = new SocketProcessor(s); // �������
                                                                              // �����-���������
                    final Thread thread = new Thread(processor); // �������
                                                                 // ���������
                                                                 // �����������
                                                                 // ���� ������
                                                                 // �� ������
                    thread.setDaemon(true); // ������ �� � ������ (����� ��
                                            // ������� �� ��������)
                    thread.start(); // ���������
                    queue.offer(processor); // ��������� � ������ ��������
                    // �����-�����������
                } // ��� ������ � �������. ���� ������� ������� (new
                  // SocketProcessor()) ����������,
                  // �� ��������� ������ �������, ���� ��������� �� �����, �
                  // ������ �� ��������
                catch (IOException ioe) {
                    Client.log.info(ioe);

                }
            }
        }
    }

    /**
     * ������� ����� �����������.
     * 
     * @return ����� ������ �����������
     */
    private Socket getNewConn() {
        Socket s = null;
        try {
            s = serverSocket.accept();
        } catch (IOException ioe) {
            Client.log.info(ioe);
            shutdownServer(); // ���� ������ � ������ ������ - "�����" ������
        }
        return s;
    }

    /**
     * ����� "��������" �������
     */
    private synchronized void shutdownServer() {
        // ������������ ������ ������� ���������, ��������� ������
        for (SocketProcessor s : queue) {
            s.close();
        }
        if (!serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ioe) {
                Client.log.info(ioe);
            }
        }
    }

    public static final Logger log = Logger.getLogger(Server.class);

    /**
     * ������� ����� ���������
     * 
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        new Server(9211).run(); // ���� ������ �� ��������, ���������
        // ������� �� ���������, � ����� run() �� �����������
    }

    /**
     * ��������� ����� ����������� ��������� ������ ��������.
     */
    private class SocketProcessor implements Runnable {
        Socket socket; // ��� �����
        BufferedReader in; // ��������������� �������� ������
        BufferedWriter out; // ���������������� �������� � �����

        /**
         * ��������� �����, ������� ������� �������� � ��������. ���� ��
         * ���������� - �������� ��� �������� �������
         * 
         * @param socketParam
         *            �����
         * @throws IOException
         *             ���� ������ � �������� br || bw
         */
        SocketProcessor(Socket socketParam) throws IOException {
            socket = socketParam;
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        }

        /**
         * ������� ���� ������ ���������/��������
         */
        @SuppressWarnings("resource")
        public void run() {
            Client.log.info("New client connected");
            while (!socket.isClosed()) { // ���� ����� �� ������...
                String line = null;
                try {
                    line = in.readLine(); // ������� ��������.
                } catch (IOException ioe) {
                    Client.log.info(ioe);
                    close(); // ���� �� ���������� - ��������� �����.
                }
                Client.log.info("Message from " + line);
                
                if (line == null ||line.equals("exit")) { // ���� ������ "exit" - ������
                                           // ����������.
                    close(); // ��������� �����
                } else if ("shutdown".equals(line)) { // ���� ��������� �������
                                                      // "�������� ������",
                                                      // ��...
                    serverThread.interrupt(); // ������� �������� ���� �
                                              // �������� ���� � �������������
                                              // ����������.
                    try {
                        new Socket("localhost", port); // ������� ����-�������
                                                       // (����� ����� ��
                                                       // .accept())
                    } catch (IOException ioe) {
                        Client.log.info(ioe);

                    } finally {
                        shutdownServer(); // � ����� ������ ������ ������� ���
                                          // ������ shutdownServer().
                    }
                } else { // ����� - ��������� �������� �� ������
                         // �����-�����������
                    for (SocketProcessor sp : queue) {
                        sp.send(line);
                    }
                }
            }
        }

        /**
         * ����� �������� � ����� ���������� ������
         * 
         * @param line
         *            ������ �� �������
         */
        public synchronized void send(String line) {
            try {
                out.write(line); // ����� ������
                out.write("\n"); // ����� ������� ������
                out.flush(); // ����������
            } catch (IOException ioe) {
                Client.log.info(ioe);
                close(); // ���� ���� � ������ �������� - ��������� ������
                         // �����.
            }
        }

        /**
         * ����� ��������� ��������� ����� � ������� ��� �� ������ ��������
         * �������
         */
        public synchronized void close() {
            queue.remove(this); // ������� �� ������
            if (!socket.isClosed()) {
                try {
                    socket.close(); // ���������
                } catch (IOException ioe) {
                    Client.log.info(ioe);
                }
            }
        }
    }
}
