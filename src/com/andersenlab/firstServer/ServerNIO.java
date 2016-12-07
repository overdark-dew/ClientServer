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
 * ����� �������. ������ ��� � ����� ����.
 */
public class ServerNIO implements Runnable{
    private final Selector selector;
    private final ServerSocketChannel ssc;
    private byte[] buffer = new byte[2048];
    private CharBuffer cb = CharBuffer.allocate(2048);
    private Charset ch = Charset.forName("UTF-8");
    private CharsetDecoder decoder = ch.newDecoder();
    //����, ��� �������� ��� SelectionKey � ��������� � ��� ByteBuffer ��� ��������
    Map<SelectionKey, ByteBuffer> connections = new HashMap<SelectionKey, ByteBuffer>();
 
    /**
     * ����������� ������� �������
     * @param port ����, ��� ����� ������� �������� ���������.
     * @throws IOException ���� �� �������� ������� ������-�����, ������� �� ���������, ������ ������� �� ����� ������
     */
    public ServerNIO(int port) throws IOException {
        ssc = ServerSocketChannel.open(); // ������� ����������� �����
        ssc.configureBlocking(false); // ��������� ����� ������������ � ��������
        ssc.socket().bind(new InetSocketAddress(port)); // �������� ������� �����������, ������� ��������� �� ������ ����
        selector = Selector.open(); // ������� �������� ���������
        ssc.register(selector, SelectionKey.OP_ACCEPT); // �������������� �� ��������� �� ������-�����.
        System.out.println("Server Started");
    }
 
    /**
     * ������� ���� �������������/�������� ��������.
     */
    public void run() {
        while (true) { //����������� ����, ����...
            try {
                if (selector.isOpen()) {
                    selector.select();  // ��������������� �� �������� ������� �� ������ �� ����������� �������.
                    Set<SelectionKey> keys = selector.selectedKeys(); // �������� ����� ������ (������ - ����)
                    for (SelectionKey sk:keys) {
                        if (!sk.isValid()) {
                            continue;
                        }
                        if (sk.isAcceptable()) { // ���� � ��� �������...
                            ServerSocketChannel ssca = (ServerSocketChannel)sk.channel(); // (ssca == ssc, ������)
                            SocketChannel sc = ssca.accept(); // ��� ��� ����� ��������, ��� ������� ������� - ��� �� ��� ���������
                            sc.configureBlocking(false); // ��������� ����� ������������
                            // ������������� ������ �� ������� ������� ������
                            SelectionKey skr = sc.register(selector, SelectionKey.OP_READ);
                            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                            connections.put(skr, byteBuffer);
                            //q.offer(skr); // � ����� � ������� ��������.
                        } else if (sk.isReadable()){ // ���� � ��� �������...
                            SocketChannel socketChannel= (SocketChannel)sk.channel(); // ������� ����� ��������
                            int read;
                            ByteBuffer byteBuffer = connections.get(sk);
                            byteBuffer.clear(); // ������� ���� ����� (��� �� ����� ���������� - ��� �������)
                            try {
                                read = socketChannel.read(byteBuffer); // ������� ��������� �����
                            } catch (IOException e) { // ������� �����
                                closeChannel(sk); // ���������
                                break; // ������� �� �����
                            }
                            if (read == -1) { // ������� ��������� � ������� ������
                                closeChannel(sk); // ���� ���������
                                break;
                            } else if (read > 0) { // ���� ���-�� ��������� �� ������ � �������� � �����...
                                byteBuffer.flip(); // ������� ����� ��� ������.
                                byteBuffer.mark(); // ������ ����� (��� ������� ��� ������� ��������� �������)
                                if (decodeAndCheck(read, byteBuffer)) break; // ���� ������� ������ - "��������� ������", ��������� ����
                                byteBuffer.reset(); // ���� �� ��������� ������ - �� ������������ �� ����� (������, ��� ������ 0) :)
                                final int pos = byteBuffer.position(); // ���������� ��� �������� ������������ � ��������� ��������
                                final int lim = byteBuffer.limit();
                                // �������� ��� ������� "����-��� ����������"
                                Set <Map.Entry<SelectionKey, ByteBuffer>> entries = connections.entrySet();
                                for (Map.Entry<SelectionKey, ByteBuffer> entry: entries) { //���� �� �������
                                    SelectionKey selectionKey = entry.getKey(); // �������� ���� �� ������
                                    selectionKey.interestOps(SelectionKey.OP_WRITE); //���������� � ����� "���� ������!"
                                    ByteBuffer entryBuffer = entry.getValue(); //�������� ����-������ �� ������
                                    entryBuffer.position(pos); // ����������� ���, ����� ��������� �������� � �����.
                                    entryBuffer.limit(lim);
                                }
                            }
                        } else if (sk.isWritable()) { // ��������� � ���������� ������ ��� ������
                            ByteBuffer bb = connections.get(sk); // �������� ����-������, ��������������� � ������
                            SocketChannel s = (SocketChannel)sk.channel(); // ����������� �����
                            try {
                                int result = s.write(bb); // ������� ��������
                                if (result == -1) { // socket properly closed
                                    closeChannel(sk); // �� �������, ��������� �������
                                }
                            } catch (IOException e2) { // � ��� ���� ����� ��������� � ������������
                                closeChannel(sk); // ���� ���������
                            }
                            if (bb.position() == bb.limit()) { 
                                sk.interestOps(SelectionKey.OP_READ); //����� ���� ����������� � ����� "����� ������!"
                            }
                        }
                    }
                    keys.clear(); // ������� ��� ������, �� �� ���� ���������� ���.
                } else break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
 
 
    /**
     * ������� - ���������� ����� ������ � ���������� � ����� ��������. ������, �� ����, ���� �����
     * ���������� ������� shutdown � ������ ������ � ���������� ��-������, �� �������� ��������, ���
     * ������������.
     * @param read - ������� ������ ��������� �� ������.
     * @param ba   - ByteBuffer to decode
     * @return true - ������ ��������
     */
    private boolean decodeAndCheck(int read, ByteBuffer ba) {
        cb.clear(); // ������� CharBuffer ����� ��������������...
        // ��� �������������, �� ������, ���� ��������, ��� ��� ���������� ���� ��������� �����.
        decoder.decode(ba, cb, false);
        cb.flip(); // � ��� ����� ������� ��������� ������ ����� �������������
        if ("shutdown\n".equals(cb.toString())) { //���������, ��� ��� "shutdown"
            shutdownServer(); // ���� �� - ��������� ������
            return true;
        }
        return false;
    }
 
    /**
     * ����� ��������� ����� ������, ������� �� ������ �������� ������ � ������� �� ������ ��������
     * @param sk - ����, ��������� � �������
     * @throws IOException - ���� ��� �������� ������ ������
     */
    private void closeChannel(SelectionKey sk) throws IOException {
        connections.remove(sk); // ������� �� ������ ��������
        SocketChannel socketChannel = (SocketChannel)sk.channel();
        if (socketChannel.isConnected()) {
            socketChannel.close(); // ��������� �����
        }
        sk.cancel(); // ������� �� ������ ���������
    }
 
 
    /**
     * ����� "��������" �������
     */
    private synchronized void shutdownServer() {
        // ������������ ������ ������� ���������, ��������� ������
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
     * ������� ����� ���������
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException{
        new ServerNIO(9211).run(); // ���� ������ �� ��������, ���������
        // ������� �� ���������, � ����� run() �� �����������
    }
 
}