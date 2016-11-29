package com.andersenlab.firstServer;

import java.net.*;
import java.io.*;

public class Client { 
    public static void main(String[] args) {
        int serverPort = 12121; // здесь об€зательно нужно указать порт к которому прив€зываетс€ сервер.
        String address = "127.0.0.1"; // это IP-адрес компьютера, где исполн€етс€ наша серверна€ программа. 
                                      // «десь указан адрес того самого компьютера где будет исполн€тьс€ и клиент.

        try {
            InetAddress ipAddress = InetAddress.getByName(address); // создаем объект который отображает вышеописанный IP-адрес.
            System.out.println("Any of you heard of a socket with IP address " + address + " and port " + serverPort + "?");
            Socket socket = new Socket(ipAddress, serverPort); // создаем сокет использу€ IP-адрес и порт сервера.
            System.out.println("Yes! I just got hold of the program.");


            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);


            // —оздаем поток дл€ чтени€ с клавиатуры.
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
            String line = null;
            System.out.println("Type in something and press enter. Will send it to the server and tell ya what it thinks.");
            System.out.println();

            while (!"exit".equals(line)) {
                line = keyboard.readLine(); // ждем пока пользователь введет что-то и нажмет кнопку Enter.
                System.out.println("Sending this line to the server...");
                out.write(line); // отсылаем введенную строку текста серверу.
                out.flush(); // заставл€ем поток закончить передачу данных.
                line = in.readLine(); // ждем пока сервер отошлет строку текста.
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