package com.andersenlab.firstServer;

import java.net.*;
import java.io.*;

public class Server {

    public static class ConnectionHandler extends Thread {

        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public ConnectionHandler(Socket socket) {

            this.socket = socket;
            

            try {
                
                in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
                out = new PrintWriter(this.socket.getOutputStream(), true);
                
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        @Override
        public void run() {

            try {

                String line = null;

                while (true) {

                    line = in.readLine(); 
                    
                    if(line.equals("exit")) break;
                                        
                    System.out.println("The dumb client just sent me this line : " + line);
                    System.out.println("I'm sending it back...");
                    out.write(line); // отсылаем клиенту обратно ту самую
                                     // строку
                                     // текста.
                    out.flush(); // заставляем поток закончить передачу
                                 // данных.
                    System.out.println("Waiting for the next line...");
                    System.out.println();
                }

            } catch (IOException e) {
                System.out.println("IO Error " + e);
            }
        }
    }

    public static void main(String[] args) {
        int port = 4777; // случайный порт (может быть любое число от 1025 до
                         // 65535)
        ServerSocket ss = null;
        try {

            try {
                ss = new ServerSocket(port); // создаем сокет сервера и
            } catch (IOException e) {
                System.out.println("Error");
                return;
            } // привязываем его к
              // вышеуказанному порту
            System.out.println("Waiting for a client...");

            while (true) {

                Socket socket = null;
                try {
                    socket = ss.accept(); // заставляем сервер ждать
                                          // подключений и выводим сообщение
                                          // когда кто-то связался с сервером
                    System.out.println("Got a client :) ... Finally, someone saw me through all the cover!");
                    System.out.println();

                    ConnectionHandler handler = new ConnectionHandler(socket);
                    handler.start();

                } catch (IOException e) {
                    System.out.println("IO Error " + e + e.getMessage() + e.getCause());
                } finally {
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException e) {

                            // TODO Auto-generated catch block
                            System.out.println("Close Error " + e);
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