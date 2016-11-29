package com.andersenlab.firstServer;

import java.net.*;
import java.io.*;

public class Client { 
    public static void main(String[] args) {
        int serverPort = 12121; // ����� ����������� ����� ������� ���� � �������� ������������� ������.
        String address = "127.0.0.1"; // ��� IP-����� ����������, ��� ����������� ���� ��������� ���������. 
                                      // ����� ������ ����� ���� ������ ���������� ��� ����� ����������� � ������.

        try {
            InetAddress ipAddress = InetAddress.getByName(address); // ������� ������ ������� ���������� ������������� IP-�����.
            System.out.println("Any of you heard of a socket with IP address " + address + " and port " + serverPort + "?");
            Socket socket = new Socket(ipAddress, serverPort); // ������� ����� ��������� IP-����� � ���� �������.
            System.out.println("Yes! I just got hold of the program.");


            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);


            // ������� ����� ��� ������ � ����������.
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
            String line = null;
            System.out.println("Type in something and press enter. Will send it to the server and tell ya what it thinks.");
            System.out.println();

            while (!"exit".equals(line)) {
                line = keyboard.readLine(); // ���� ���� ������������ ������ ���-�� � ������ ������ Enter.
                System.out.println("Sending this line to the server...");
                out.write(line); // �������� ��������� ������ ������ �������.
                out.flush(); // ���������� ����� ��������� �������� ������.
                line = in.readLine(); // ���� ���� ������ ������� ������ ������.
                System.out.println("The server was very polite. It sent me this : " + line);
                System.out.println("Looks like the server is pleased with us. Go ahead and enter more lines.");
                System.out.println();
            }
            socket.close();
        } catch (Exception x) {
            x.printStackTrace();
        }
    }
}