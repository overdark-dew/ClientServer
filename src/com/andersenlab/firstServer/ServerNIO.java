package com.andersenlab.firstServer;

import java.io.*;
import java.net.InetSocketAddress;
//import java.net.Socket;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.LinkedBlockingQueue;
import java.util.*;
import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
 
/**
 * Класс сервера. Делает все в одной нити.
 */
public class ServerNIO implements Runnable{
    private final Selector selector;
    private final ServerSocketChannel ssc;
    private byte[] buffer = new byte[2048];
    private CharBuffer cb = CharBuffer.allocate(2048);
    private Charset ch = Charset.forName("UTF-8");
    private CharsetDecoder decoder = ch.newDecoder();
    //Мапа, где хранятся все SelectionKey и связанные с ним ByteBuffer для рассылки
    Map<SelectionKey, ByteBuffer> connections = new HashMap<SelectionKey, ByteBuffer>();
 
    /**
     * Конструктор объекта сервера
     * @param port Порт, где будем слушать входящие сообщения.
     * @throws IOException Если не удасться создать сервер-сокет, вылетит по эксепшену, объект Сервера не будет создан
     */
    public ServerNIO(int port) throws IOException {
        ssc = ServerSocketChannel.open(); // создаем серверСокет канал
        ssc.configureBlocking(false); // отключаем режим блокирования в ожидании
        ssc.socket().bind(new InetSocketAddress(port)); // получаем обычный серверсокет, который биндиться на нужный порт
        selector = Selector.open(); // создаем селектор прослушки
        ssc.register(selector, SelectionKey.OP_ACCEPT); // регистрируемся на селекторе на сервер-канал.
        System.out.println("Server Started");
    }
 
    /**
     * главный цикл прослушивания/ожидания коннекта.
     */
    public void run() {
        while (true) { //бесконечный цикл, типа...
            try {
                if (selector.isOpen()) {
                    selector.select();  // останавливаемся на ожиданни события от любого из подписанных каналов.
                    Set<SelectionKey> keys = selector.selectedKeys(); // получаем набор ключей (обычно - один)
                    for (SelectionKey sk:keys) {
                        if (!sk.isValid()) {
                            continue;
                        }
                        if (sk.isAcceptable()) { // если к нам коннект...
                            ServerSocketChannel ssca = (ServerSocketChannel)sk.channel(); // (ssca == ssc, кстати)
                            SocketChannel sc = ssca.accept(); // так как точно известно, что ожидает коннект - тут мы без остановки
                            sc.configureBlocking(false); // отключаем режим блокирования
                            // подписываемся только на события прихода данных
                            SelectionKey skr = sc.register(selector, SelectionKey.OP_READ);
                            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                            connections.put(skr, byteBuffer);
                            //q.offer(skr); // и ложим в очередь рассылки.
                        } else if (sk.isReadable()){ // если к нам посылка...
                            SocketChannel socketChannel= (SocketChannel)sk.channel(); // хватаем канал коннекта
                            int read;
                            ByteBuffer byteBuffer = connections.get(sk);
                            byteBuffer.clear(); // очищаем байт буфер (кто не успел записаться - тот опоздал)
                            try {
                                read = socketChannel.read(byteBuffer); // пробуем заполнить буфер
                            } catch (IOException e) { // коннект отпал
                                closeChannel(sk); // закрываем
                                break; // выходим из цикла
                            }
                            if (read == -1) { // коннект отвалился в штатном режиме
                                closeChannel(sk); // тоже закрываем
                                break;
                            } else if (read > 0) { // если что-то прочитали из сокета и записали в буфер...
                                byteBuffer.flip(); // готовим буфер для чтения.
                                byteBuffer.mark(); // ставим метку (ибо декодер нам сломает состояние буффера)
                                if (decodeAndCheck(read, byteBuffer)) break; // если декодер сказал - "выключаем сервер", прерываем цикл
                                byteBuffer.reset(); // если не выключаем сервер - то возвращаемся на метку (кстати, это всегда 0) :)
                                final int pos = byteBuffer.position(); // запоминаем для быстрого проставления у остальных буфферов
                                final int lim = byteBuffer.limit();
                                // получаем сет наборов "ключ-его байтбуффер"
                                Set <Map.Entry<SelectionKey, ByteBuffer>> entries = connections.entrySet();
                                for (Map.Entry<SelectionKey, ByteBuffer> entry: entries) { //цикл по наборам
                                    SelectionKey selectionKey = entry.getKey(); // получаем ключ из набора
                                    selectionKey.interestOps(SelectionKey.OP_WRITE); //переключам в режим "хочу писать!"
                                    ByteBuffer entryBuffer = entry.getValue(); //получаем байт-буффер из набора
                                    entryBuffer.position(pos); // настраиваем его, чтобы правильно записать в сокет.
                                    entryBuffer.limit(lim);
                                }
                            }
                        } else if (sk.isWritable()) { // сообщение о готовности сокета для записи
                            ByteBuffer bb = connections.get(sk); // получаем байт-буффер, ассоциированный с ключем
                            SocketChannel s = (SocketChannel)sk.channel(); // выдергиваем канал
                            try {
                                int result = s.write(bb); // пробуем записать
                                if (result == -1) { // socket properly closed
                                    closeChannel(sk); // ну понятно, закрываем коннект
                                }
                            } catch (IOException e2) { // а это если отвал произошел в моментзаписи
                                closeChannel(sk); // тоже закрываем
                            }
                            if (bb.position() == bb.limit()) { 
                                sk.interestOps(SelectionKey.OP_READ); //сразу ключ переключаем в режим "хотим читать!"
                            }
                        }
                    }
                    keys.clear(); // очищаем сет ключей, мы по идее обработали все.
                } else break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
 
 
    /**
     * Декодер - декодирует поток байтов и превращает в поток символов. Вообще, по сути, было проще
     * превратить команду shutdown в массив байтов и сравнивать по-байтно, но хотелось показать, как
     * декодировать.
     * @param read - сколько байтов прочитали из сокета.
     * @param ba   - ByteBuffer to decode
     * @return true - сервер выключен
     */
    private boolean decodeAndCheck(int read, ByteBuffer ba) {
        cb.clear(); // очищаем CharBuffer перед декодированием...
        // это декодирование, не полное, надо признать, ибо нас интересует лишь стартовая фраза.
        decoder.decode(ba, cb, false);
        cb.flip(); // в чар буфер свалена пришедшая строка после декодирования
        if ("shutdown\n".equals(cb.toString())) { //проверяем, что это "shutdown"
            shutdownServer(); // если да - выключаем сервер
            return true;
        }
        return false;
    }
 
    /**
     * Метод закрывает канал сокета, снимает со списка активных ключей и удаляет из списка рассылки
     * @param sk - ключ, связанный с каналом
     * @throws IOException - если при закрытии прошла ошибка
     */
    private void closeChannel(SelectionKey sk) throws IOException {
        connections.remove(sk); // удаляем из списка рассылки
        SocketChannel socketChannel = (SocketChannel)sk.channel();
        if (socketChannel.isConnected()) {
            socketChannel.close(); // закрываем канал
        }
        sk.cancel(); // удаляем из списка селектора
    }
 
 
    /**
     * метод "глушения" сервера
     */
    private synchronized void shutdownServer() {
        // обрабатываем список рабочих коннектов, закрываем каждый
        Set<SelectionKey> skSet = connections.keySet();
        for (SelectionKey sq:skSet) {
            SocketChannel s = (SocketChannel)sq.channel();
            if (s.isConnected()) {
                try {
                    s.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (ssc.isOpen()) {
            try {
                ssc.close();
                selector.close();
            } catch (IOException ignored) {}
        }
    }
 
    /**
     * входная точка программы
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException{
        new ServerNIO(9211).run(); // если сервер не создался, программа
        // вылетит по эксепшену, и метод run() не запуститься
    }
 
}