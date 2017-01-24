package com.hyunjae.xdcc2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;

public class FileTransfer implements Runnable, Closeable {

    private static final Logger logger = LoggerFactory.getLogger(FileTransfer.class);

    private static final int BIG_BUFFER_SIZE = 1024;

    private String filename;
    private InetAddress addr;
    private int port;

    private SocketChannel socketChannel;
    private FileChannel fileChannel;

    public FileTransfer(String filename, InetAddress addr, int port) {
        this.filename = filename;
        this.addr = addr;
        this.port = port;
    }

    public void start() throws IOException {
        socketChannel = SocketChannel.open();
        SocketAddress remote = new InetSocketAddress(addr, port);
        socketChannel.connect(remote);

        String file = System.getProperty("user.home") + "/Downloads/" + filename;
        fileChannel = new FileOutputStream(file).getChannel();

        Thread thread = new Thread(this);
        thread.start();
    }

    @Override
    public void close() throws IOException {
        fileChannel.close();
        socketChannel.close();
    }

    @Override
    public void run() {
        try {
            ByteBuffer inBuffer = ByteBuffer.allocateDirect(BIG_BUFFER_SIZE); // TODO
            ByteBuffer outBuffer = ByteBuffer.allocate(4);

            int bytesRead;
            long bytesTransferred = 0;

            while((bytesRead = socketChannel.read(inBuffer)) != -1) {
                inBuffer.flip();
                fileChannel.write(inBuffer);
                inBuffer.clear();

                //Convert bytesTransfered to an "unsigned, 4 byte integer in network byte order", per DCC specification
                bytesTransferred += bytesRead;
                outBuffer.put(0, (byte) ((bytesTransferred >> 24) & 0xff));
                outBuffer.put(1, (byte) ((bytesTransferred >> 16) & 0xff));
                outBuffer.put(2, (byte) ((bytesTransferred >> 8) & 0xff));
                outBuffer.put(3, (byte) (bytesTransferred & 0xff));
                outBuffer.flip();
                socketChannel.write(outBuffer);
                outBuffer.clear();
            }

            logger.debug("File transfer complete : {}", filename);
        } catch (IOException e) {
            logger.error(e.getMessage());
        } finally {
            try {
                close();
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
    }
}
