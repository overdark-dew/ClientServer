package com.andersenlab.firstServer;

import java.net.Socket;

import org.apache.log4j.Logger;

import java.io.*;

/**
 * �����-������ ���-�������.
 */

public class Client {
    final Socket socket; // ��� ����� ����� ��� �������
    final BufferedReader socketReader; // ���������������� �������� � �������
    final BufferedWriter socketWriter; // ���������������� �������� �� ������
    final BufferedReader userInput; // ���������������� ��������
                                    // ����������������� ����� � �������
    final String name;

    /**
     * ����������� ������� �������
     * 
     * @param host
     *            - IP ����� ��� localhost ��� �������� ���
     * @param port
     *            - ����, �� ������� ����� ������
     * @throws java.io.IOException
     *             - ���� �� ������ ���������������, �������� ����������, �����
     *             ������������� �������� �������
     */
    public Client(String name, String host, int port) throws IOException {

        this.name = name + ": "; // ��� ������������
        socket = new Socket(host, port); // ������� �����
        // ������� �������� � �������� � ����� � �������� ���������� UTF-8
        socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        socketWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        // ������� �������� � ������� (�� ������������)
        userInput = new BufferedReader(new InputStreamReader(System.in));
        new Thread(new Receiver()).start();// ������� � ��������� ����
                                           // ������������ ������ �� ������
    }

    /**
     * �����, ��� ���������� ������� ���� ������ ��������� � ������� � ��������
     * �� ������
     */
    public void run() {
        Client.log.info("Enter the message: ");
        while (true) {
            String userString = null;
            try {
                userString = userInput.readLine(); // ������ ������ ��
                                                   // ������������
            } catch (IOException ioe) {
                Client.log.info(ioe);
            } // � ������� ��������� �� ����� ���� � ��������, ����������
              // ���� ���-�� �� ��� ��� ������������ ������ ����� Enter...
            if (userString == null || userString.equals("exit") || socket.isClosed()) {
                close(); // ...��������� �������.
                break; // �� ����� break �� �� ������, �� ����� ��, �����
                       // ���������� �� �������
            } else { // ...�����...
                try {
                    socketWriter.write(name);
                    socketWriter.write(userString); // ����� ������ ������������
                    socketWriter.write("\n"); // ��������� "����� ������", ����
                                              // readLine() ������� ��������
                    socketWriter.flush(); // ����������
                } catch (IOException ioe) {
                    Client.log.info(ioe);
                    close(); // � ����� ������ - ���������.
                }
            }
        }
    }

    /**
     * ����� ��������� ������� � ������� �� ��������� (��� ������������ �����
     * �������� ������ BufferedReader.readLine(), �� �������� ������������)
     */
    public synchronized void close() {// ����� ���������������, ����� ���������
                                      // ������� ��������.
        if (!socket.isClosed()) { // ���������, ��� ����� �� ������...
            try {
                socket.close(); // ���������...
                System.exit(0); // �������!
            } catch (IOException ioe) {
                Client.log.info(ioe);

            }
        }
    }

    public static final Logger log = Logger.getLogger(Client.class);

    public static void main(String[] args) { // ������� ����� ���������

        Client.log.info("Enter you name: ");
        String name = null;
        BufferedReader nameReader = new BufferedReader(new InputStreamReader(System.in));
        try {
            name = nameReader.readLine();
        } catch (IOException ioe) {

            Client.log.info(ioe);
        }

        try {
            new Client(name, "localhost", 9211).run(); // �������
            // ��������������...
        } catch (IOException ioe) { // ���� ������ �� ������...
            Client.log.info("Unable to connect. Server not running?");
            Client.log.info(ioe);
        }
    }

    /**
     * ��������� ��������� ����� ������������ ������
     */
    private class Receiver implements Runnable {
        /**
         * run() ��������� ����� ������� ���� �� ������������ ������� ����.
         */
        public void run() {
            while (!socket.isClosed()) { // ����� ��������� �������.
                String line = null;
                try {
                    line = socketReader.readLine(); // ������� ��������
                } catch (IOException ioe) { // ���� � ������ ������ ������,
                                            // ��...
                    // ��������, ��� ��� �� ��������� ������� �������� ������
                    // ��������
                    Client.log.info(ioe);
                    if ("Socket closed".equals(ioe.getMessage())) {
                        // ChatClient.log.info(ioe);
                        break;
                    }
                    // ChatClient.log.(message);
                    Client.log.info("Connection lost"); // � ���� �� �������
                                                            // � ������ ������
                                                            // ����.
                    close(); // �� � ��������� ����� (������, ��������� �����
                             // ������ ChatClient, ���� ������)
                }
                if (line == null) { // ������ ����� null ���� ������ �������
                                    // ������� �� ����� ����������, ����
                                    // ��������
                    Client.log.info("Server has closed connection");
                    close(); // ...�����������
                } else { // ����� �������� ��, ��� ������� ������.
                    Client.log.info("Message from " + line);
                }
            }
        }
    }
}