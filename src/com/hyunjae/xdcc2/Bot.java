package com.hyunjae.xdcc2;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;

public class Bot implements Runnable, Closeable{

    private static final Logger logger = LoggerFactory.getLogger(Bot.class);

    private static final int DEFAULT_PORT = 6667;

    // Command responses
    private static final String RPL_WELCOME = "001";
    private static final String ERR_NICKNAMEINUSE = "433";

    // Constructor params
    private String server;
    private int port;
    private String nick;
    private String channels[];

    private List<String> joinedChannels = Lists.newArrayList();

    // Closeables
    private Socket socket;
    private BufferedWriter writer;
    private BufferedReader reader;

    public Bot(String server, String nick, String[] channels) {
        this(server, DEFAULT_PORT, nick, channels);
    }

    public Bot(String server, int port, String nick, String channels[]) {
        this.server = server;
        this.port = port;
        this.nick = nick;
        this.channels = channels;
    }

    public void start() throws IOException {
        socket = new Socket(server, port);
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        Thread thread = new Thread(this);
        thread.start();
    }

    private void sendRaw(String line) {
        logger.debug(line);

        try {
            writer.write(line + "\r\n");
            writer.flush();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    public void sendXDCC(String botName, String packNumber) {
        sendRaw("PRIVMSG " + botName + " :xdcc send #" + packNumber);
    }

    public void changeNick(String nick) {
        this.nick = nick;
        sendRaw("NICK " + nick);
    }

    public boolean containsChannel(String channel) {
        return joinedChannels.contains(channel);
    }

    private static List<String> tokenizeLine(String rawLine) {
        List<String> strings = Lists.newArrayList();
        if (Strings.isNullOrEmpty(rawLine))
            return strings;

        String line = CharMatcher.WHITESPACE.trimFrom(rawLine);

        int pos = 0;
        int end;

        while((end = line.indexOf(' ', pos)) >= 0) {
            strings.add(line.substring(pos, end));
            pos = end + 1;
            if(line.charAt(pos) == ':') {
                strings.add(line.substring(pos));
                return strings;
            }
        }

        strings.add(line.substring(pos));
        return strings;
    }

    private static List<String> tokenizePrefix(String rawPrefix) {
        List<String> args = Lists.newArrayList();
        if(Strings.isNullOrEmpty(rawPrefix))
            return args;

        String prefix = CharMatcher.WHITESPACE.trimFrom(rawPrefix);

        int pos = 0;
        int end;

        if (prefix.charAt(0) == ':')
            pos++;
        if ((end = prefix.indexOf('!', pos)) >= 0) {
            args.add(prefix.substring(pos, end));
            pos = end;
        }
        if ((end = prefix.indexOf('@', pos)) >= 0) {
            args.add(prefix.substring(pos, end));
            pos = end;
        }
        args.add(prefix.substring(pos));
        return args;
    }

    private static List<String> tokenizeParams(String rawParam) {
        List<String> params = Lists.newArrayList();
        if (Strings.isNullOrEmpty(rawParam))
            return params;

        String param = CharMatcher.WHITESPACE.trimFrom(rawParam);

        int pos = 0;
        int end;

        if (param.charAt(0) == ':')
            pos++;

        while ((end = param.indexOf(' ', pos)) >= 0) {
            params.add(param.substring(pos, end));
            pos = end + 1;
        }

        params.add(param.substring(pos));
        return params;
    }

    private void handleLine(String line) {
        List<String> lines = tokenizeLine(line);

        String prefix = "";
        String command = "";

        if(lines.size() > 0 && lines.get(0).charAt(0) == ':')
            prefix = lines.remove(0);
        if(lines.size() > 0)
            command = lines.remove(0);

        switch(command) {
            case "PING":
                sendRaw("PONG " + lines.get(0));
                break;

            case RPL_WELCOME:
                if (channels == null || channels.length == 0)
                    break;

                StringBuilder builder = new StringBuilder("JOIN ");
                for (String channel : channels) {
                    builder.append(channel);
                    builder.append(','); // TODO: Remove last ',' of the message
                }

                sendRaw(builder.toString());
                break;

            case ERR_NICKNAMEINUSE:
                changeNick(nick + "_");
                break;

            case "JOIN":
                List<String> prefixes = tokenizePrefix(prefix);
                if(prefixes.size() > 0 && prefixes.get(0).equals(nick)) {
                    List<String> params = tokenizeParams(lines.get(0));
                    if(params.size() > 0)
                        joinedChannels.add(params.get(0));
                }
                break;

            case "PRIVMSG":
                if (lines.size() > 1)
                    handleCTCP(lines.get(1)); // get(0) is <target>
                break;
        }
    }

    private void handleCTCP(String rawParam) {
        String param;
        if (rawParam.charAt(0) == ':')
            param = rawParam.substring(1);
        else
            param = rawParam;

        List<String> params;
        if (param.startsWith("\u0001") && param.endsWith("\u0001"))
            params = tokenizeParams(param.substring(1, param.length() - 1));
        else
            return;

        if(params.size() > 0 && params.remove(0).equals("DCC")) {
            if(params.size() > 0 && params.remove(0).equals("SEND")) {
                String filename = "";
                InetAddress addr = null;
                int port = -1;
                long fileSize = -1;

                if (params.size() > 0)
                    filename = params.remove(0);
                if (params.size() > 0)
                    addr = InetAddresses.fromInteger(Integer.parseInt(params.remove(0)));
                if (params.size() > 0)
                    port = Integer.parseInt(params.remove(0));
                if (params.size() > 0)
                    fileSize = Long.parseLong(params.remove(0));

                logger.debug("filename: {}, ip: {}, port: {}, fileSize: {}", filename, addr, port, fileSize);

                FileTransfer transfer = new FileTransfer(filename, addr, port);
                try {
                    transfer.start();
                } catch (IOException e) {
                    logger.error(e.getMessage());
                }
            }
        }
    }

    @Override
    public void run() {
        sendRaw("USER " + nick + " 8 * : bot");
        sendRaw("NICK " + nick);

        try {
            String line;
            while((line = reader.readLine()) != null) {
                logger.debug(line);
                handleLine(line);
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
