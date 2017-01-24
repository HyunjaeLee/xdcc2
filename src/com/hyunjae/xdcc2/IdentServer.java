package com.hyunjae.xdcc2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class IdentServer implements Runnable, Closeable{

    private static final Logger logger = LoggerFactory.getLogger(IdentServer.class);

    private static final int DEFAULT_PORT = 113;

    private ServerSocket serverSocket;

    public IdentServer() throws IOException {
        this(DEFAULT_PORT);
    }

    public IdentServer(int port) throws IOException { // Allows custom port for port-forwarding
        serverSocket = new ServerSocket(port);
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.setName("IdentServer");
        thread.start();
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }

    @Override
    public void run() {
        while(!serverSocket.isClosed()) {
            try(Socket socket = serverSocket.accept()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                String line = reader.readLine();
                String response = line + " : USERID : UNIX : user\r\n";
                writer.write(response);
                writer.flush();
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
    }
}
