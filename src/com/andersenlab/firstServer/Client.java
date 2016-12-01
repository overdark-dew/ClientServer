package com.andersenlab.firstServer;

import java.net.Socket;

import org.apache.log4j.Logger;

import java.io.*;

/**
 * Класс-клиент чат-сервера.
 */

public class Client {
    final Socket socket; // это будет сокет для сервера
    final BufferedReader socketReader; // буферизированный читатель с сервера
    final BufferedWriter socketWriter; // буферизированный писатель на сервер
    final BufferedReader userInput; // буферизированный читатель
                                    // пользовательского ввода с консоли
    final String name;

    /**
     * Конструктор объекта клиента
     * 
     * @param host
     *            - IP адрес или localhost или доменное имя
     * @param port
     *            - порт, на котором висит сервер
     * @throws java.io.IOException
     *             - если не смогли приконнектиться, кидается исключение, чтобы
     *             предотвратить создание объекта
     */
    public Client(String name, String host, int port) throws IOException {

        this.name = name + ": "; // имя пользователя
        socket = new Socket(host, port); // создаем сокет
        // создаем читателя и писателя в сокет с дефолной кодировкой UTF-8
        socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        socketWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        // создаем читателя с консоли (от пользователя)
        userInput = new BufferedReader(new InputStreamReader(System.in));
        new Thread(new Receiver()).start();// создаем и запускаем нить
                                           // асинхронного чтения из сокета
    }

    /**
     * метод, где происходит главный цикл чтения сообщений с консоли и отправки
     * на сервер
     */
    public void run() {
        Client.log.info("Enter the message: ");
        while (true) {
            String userString = null;
            try {
                userString = userInput.readLine(); // читаем строку от
                                                   // пользователя
            } catch (IOException ioe) {
                Client.log.info(ioe);
            } // с консоли эксепшена не может быть в принципе, игнорируем
              // если что-то не так или пользователь просто нажал Enter...
            if (userString == null || userString.equals("exit") || socket.isClosed()) {
                close(); // ...закрываем коннект.
                break; // до этого break мы не дойдем, но стоит он, чтобы
                       // компилятор не ругался
            } else { // ...иначе...
                try {
                    socketWriter.write(name);
                    socketWriter.write(userString); // пишем строку пользователя
                    socketWriter.write("\n"); // добавляем "новою строку", дабы
                                              // readLine() сервера сработал
                    socketWriter.flush(); // отправляем
                } catch (IOException ioe) {
                    Client.log.info(ioe);
                    close(); // в любой ошибке - закрываем.
                }
            }
        }
    }

    /**
     * метод закрывает коннект и выходит из программы (это единственный выход
     * прервать работу BufferedReader.readLine(), на ожидании пользователя)
     */
    public synchronized void close() {// метод синхронизирован, чтобы исключить
                                      // двойное закрытие.
        if (!socket.isClosed()) { // проверяем, что сокет не закрыт...
            try {
                socket.close(); // закрываем...
                System.exit(0); // выходим!
            } catch (IOException ioe) {
                Client.log.info(ioe);

            }
        }
    }

    public static final Logger log = Logger.getLogger(Client.class);

    public static void main(String[] args) { // входная точка программы

        Client.log.info("Enter you name: ");
        String name = null;
        BufferedReader nameReader = new BufferedReader(new InputStreamReader(System.in));
        try {
            name = nameReader.readLine();
        } catch (IOException ioe) {

            Client.log.info(ioe);
        }

        try {
            new Client(name, "localhost", 9211).run(); // Пробуем
            // приконнетиться...
        } catch (IOException ioe) { // если объект не создан...
            Client.log.info("Unable to connect. Server not running?");
            Client.log.info(ioe);
        }
    }

    /**
     * Вложенный приватный класс асинхронного чтения
     */
    private class Receiver implements Runnable {
        /**
         * run() вызовется после запуска нити из конструктора клиента чата.
         */
        public void run() {
            while (!socket.isClosed()) { // сходу проверяем коннект.
                String line = null;
                try {
                    line = socketReader.readLine(); // пробуем прочесть
                } catch (IOException ioe) { // если в момент чтения ошибка,
                                            // то...
                    // проверим, что это не банальное штатное закрытие сокета
                    // сервером
                    Client.log.info(ioe);
                    if ("Socket closed".equals(ioe.getMessage())) {
                        // ChatClient.log.info(ioe);
                        break;
                    }
                    // ChatClient.log.(message);
                    Client.log.info("Connection lost"); // а сюда мы попадем
                                                            // в случае ошибок
                                                            // сети.
                    close(); // ну и закрываем сокет (кстати, вызвается метод
                             // класса ChatClient, есть доступ)
                }
                if (line == null) { // строка будет null если сервер прикрыл
                                    // коннект по своей инициативе, сеть
                                    // работает
                    Client.log.info("Server has closed connection");
                    close(); // ...закрываемся
                } else { // иначе печатаем то, что прислал сервер.
                    Client.log.info("Message from " + line);
                }
            }
        }
    }
}