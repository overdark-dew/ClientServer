package com.andersenlab.firstServer;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

/**
 * Класс сервера. принимает сообщение, создает
 * SocketProcessor на каждое сообщение
 */
public class Server {
    private ServerSocket serverSocket; // сам сервер-сокет
    private Thread serverThread; // главная нить обработки сервер-сокета
    private int port; // порт сервер сокета.
    // очередь, где храняться все SocketProcessorы для рассылки
    BlockingQueue<SocketProcessor> queue = new LinkedBlockingQueue<SocketProcessor>();

    /**
     * Конструктор объекта сервера
     * 
     * @param port
     *            Порт, где будем слушать входящие сообщения.
     * @throws IOException
     *             Если не удасться создать сервер-сокет, вылетит по эксепшену,
     *             объект Сервера не будет создан
     */
    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port); // создаем сервер-сокет
        this.port = port; // сохраняем порт.
        Client.log.info("Server started");
    }

    /**
     * главный цикл прослушивания/ожидания коннекта.
     */
    void run() {
        serverThread = Thread.currentThread(); // со старта сохраняем нить
                                               // (чтобы можно ее было
                                               // interrupt())
        while (true) { // бесконечный цикл, типа...
            Socket s = getNewConn(); // получить новое соединение или
                                     // фейк-соедиение
            if (serverThread.isInterrupted()) { // если это фейк-соединение, то
                                                // наша нить была interrupted(),
                // надо прерваться
                break;
            } else if (s != null) { // "только если коннект успешно создан"...
                try {
                    final SocketProcessor processor = new SocketProcessor(s); // создаем
                                                                              // сокет-процессор
                    final Thread thread = new Thread(processor); // создаем
                                                                 // отдельную
                                                                 // асинхронную
                                                                 // нить чтения
                                                                 // из сокета
                    thread.setDaemon(true); // ставим ее в демона (чтобы не
                                            // ожидать ее закрытия)
                    thread.start(); // запускаем
                    queue.offer(processor); // добавляем в список активных
                    // сокет-процессоров
                } // тут прикол в замысле. Если попытка создать (new
                  // SocketProcessor()) безуспешна,
                  // то остальные строки обойдем, нить запускать не будем, в
                  // список не сохраним
                catch (IOException ioe) {
                    Client.log.info(ioe);

                }
            }
        }
    }

    /**
     * Ожидает новое подключение.
     * 
     * @return Сокет нового подключения
     */
    private Socket getNewConn() {
        Socket s = null;
        try {
            s = serverSocket.accept();
        } catch (IOException ioe) {
            Client.log.info(ioe);
            shutdownServer(); // если ошибка в момент приема - "гасим" сервер
        }
        return s;
    }

    /**
     * метод "глушения" сервера
     */
    private synchronized void shutdownServer() {
        // обрабатываем список рабочих коннектов, закрываем каждый
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
     * входная точка программы
     * 
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        new Server(9211).run(); // если сервер не создался, программа
        // вылетит по эксепшену, и метод run() не запуститься
    }

    /**
     * вложенный класс асинхронной обработки одного коннекта.
     */
    private class SocketProcessor implements Runnable {
        Socket socket; // наш сокет
        BufferedReader in; // буферизировнный читатель сокета
        BufferedWriter out; // буферизированный писатель в сокет

        /**
         * Сохраняем сокет, пробуем создать читателя и писателя. Если не
         * получается - вылетаем без создания объекта
         * 
         * @param socketParam
         *            сокет
         * @throws IOException
         *             Если ошибка в создании br || bw
         */
        SocketProcessor(Socket socketParam) throws IOException {
            socket = socketParam;
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        }

        /**
         * Главный цикл чтения сообщений/рассылки
         */
        @SuppressWarnings("resource")
        public void run() {
            Client.log.info("New client connected");
            while (!socket.isClosed()) { // пока сокет не закрыт...
                String line = null;
                try {
                    line = in.readLine(); // пробуем прочесть.
                } catch (IOException ioe) {
                    Client.log.info(ioe);
                    close(); // если не получилось - закрываем сокет.
                }
                Client.log.info("Message from " + line);
                
                if (line == null ||line.equals("exit")) { // Если вводим "exit" - клиент
                                           // отключился.
                    close(); // закрываем сокет
                } else if ("shutdown".equals(line)) { // если поступила команда
                                                      // "погасить сервер",
                                                      // то...
                    serverThread.interrupt(); // сначала возводим флаг у
                                              // северной нити о необходимости
                                              // прерваться.
                    try {
                        new Socket("localhost", port); // создаем фейк-коннект
                                                       // (чтобы выйти из
                                                       // .accept())
                    } catch (IOException ioe) {
                        Client.log.info(ioe);

                    } finally {
                        shutdownServer(); // а затем глушим сервер вызовом его
                                          // метода shutdownServer().
                    }
                } else { // иначе - банальная рассылка по списку
                         // сокет-процессоров
                    for (SocketProcessor sp : queue) {
                        sp.send(line);
                    }
                }
            }
        }

        /**
         * Метод посылает в сокет полученную строку
         * 
         * @param line
         *            строка на отсылку
         */
        public synchronized void send(String line) {
            try {
                out.write(line); // пишем строку
                out.write("\n"); // пишем перевод строки
                out.flush(); // отправляем
            } catch (IOException ioe) {
                Client.log.info(ioe);
                close(); // если глюк в момент отправки - закрываем данный
                         // сокет.
            }
        }

        /**
         * метод аккуратно закрывает сокет и убирает его со списка активных
         * сокетов
         */
        public synchronized void close() {
            queue.remove(this); // убираем из списка
            if (!socket.isClosed()) {
                try {
                    socket.close(); // закрываем
                } catch (IOException ioe) {
                    Client.log.info(ioe);
                }
            }
        }
    }
}
