package com.andersenlab.firstServer;

import java.net.*;
import java.io.*;

public class Server {  

    public static class ConnectionHandler extends Thread {

        public Socket socket;

        public ConnectionHandler(Socket socket) {

            this.socket = socket;

        }

        @Override
        public void run() {

            try {

                
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                String line = null;

                while (true) {

                    line = in.readLine(); // ������� ���� ������ �������
                                         // ������
                                         // ������.
                    System.out.println("The dumb client just sent me this line : " + line);
                    System.out.println("I'm sending it back...");
                    out.write(line); // �������� ������� ������� �� �����
                                        // ������
                                        // ������.
                    out.flush(); // ���������� ����� ��������� ��������
                                 // ������.
                    System.out.println("Waiting for the next line...");
                    System.out.println();
                }

            } catch (IOException e) {
                System.out.println("IO Error " + e);
            }
        }
    }

    public static void main(String[] args) {
        int port = 4777; // ��������� ���� (����� ���� ����� ����� �� 1025 ��
                          // 65535)
        ServerSocket ss = null;
        try {

            try {
                ss = new ServerSocket(port); // ������� ����� ������� �
            } catch (IOException e) {
                System.out.println("Error");
                return;
            } // ����������� ��� �
              // �������������� �����
            System.out.println("Waiting for a client...");
            
            while (true) {

                Socket socket = null;
                try {
                    socket = ss.accept(); // ���������� ������ �����
                                          // ����������� � ������� ���������
                                          // ����� ���-�� �������� � ��������
                    System.out.println("Got a client :) ... Finally, someone saw me through all the cover!");
                    System.out.println();

                    ConnectionHandler handler = new ConnectionHandler(socket);
                    handler.start();
                    
                } catch (IOException e) {
                    System.out.println("IO Error " + e);
                } finally {
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }

                }
            }
        } finally {
            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }
}