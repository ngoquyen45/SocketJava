import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

/**
 * @author jcgonzalez.com
 */
public class ProxyMultiThread {
    public static void main(String[] args) {
        try {
            if (args.length != 3)
                throw new IllegalArgumentException("insufficient arguments");
            // and the local port that we listen for connections on
            String remoteHost = args[0];
            int remotePort = Integer.parseInt(args[1]);
            int localPort = Integer.parseInt(args[2]);
            // Print a start-up message
            System.out.println("Starting proxy for " + remoteHost + ":" + remotePort
                    + " on port " + localPort);
            ServerSocket server = new ServerSocket(localPort);
            while (true) {
                new ThreadProxy(server.accept(), remoteHost, remotePort);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // System.err.println(e);
            System.err.println("Usage: java ProxyMultiThread "
                    + "<host> <remoteport> <localport>");
        }
    }
}

/**
 * Handles a socket connection to the proxy server from the client and uses 2
 * threads to proxy between server and client
 *
 * @author jcgonzalez.com
 */
class ThreadProxy extends Thread {
    private final Socket sClient;
    private final String SERVER_URL;
    private final int SERVER_PORT;

    ThreadProxy(Socket sClient, String ServerUrl, int ServerPort) {
        this.SERVER_URL = ServerUrl;
        this.SERVER_PORT = ServerPort;
        this.sClient = sClient;
        this.start();
    }

    // https://stackoverflow.com/questions/28890907/implement-a-function-to-check-if-a-string-byte-array-follows-utf-8-format
    public static boolean isUTF8(final byte[] pText) {
        int expectedLength = 0;

        for (int i = 0; i < pText.length; i++) {
            if ((pText[i] & 0b10000000) == 0b00000000) {
                expectedLength = 1;
            } else if ((pText[i] & 0b11100000) == 0b11000000) {
                expectedLength = 2;
            } else if ((pText[i] & 0b11110000) == 0b11100000) {
                expectedLength = 3;
            } else if ((pText[i] & 0b11111000) == 0b11110000) {
                expectedLength = 4;
            } else if ((pText[i] & 0b11111100) == 0b11111000) {
                expectedLength = 5;
            } else if ((pText[i] & 0b11111110) == 0b11111100) {
                expectedLength = 6;
            } else {
                return false;
            }

            while (--expectedLength > 0) {
                if (++i >= pText.length) {
                    return false;
                }
                if ((pText[i] & 0b11000000) != 0b10000000) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void run() {
        try {
            final byte[] request = new byte[65536]; //1024
            byte[] reply = new byte[65536]; // 4096
            final InputStream inFromClient = sClient.getInputStream();
            final OutputStream outToClient = sClient.getOutputStream();
            Socket client = null, server;
            // connects a socket to the server
            try {
                server = new Socket(SERVER_URL, SERVER_PORT);
            } catch (IOException e) {
                PrintWriter out = new PrintWriter(new OutputStreamWriter(
                        outToClient));
                out.flush();
                throw new RuntimeException(e);
            }
            // a new thread to manage streams from server to client (DOWNLOAD)
            final InputStream inFromServer = server.getInputStream();
            final OutputStream outToServer = server.getOutputStream();

            // a new thread for uploading to the server
            new Thread(() -> {
                int bytes_read;
                try {
                    while ((bytes_read = inFromClient.read(request)) != -1) {
                        //TODO CREATE YOUR LOGIC HERE
                        String temp = new String(request);
//                        if (temp.contains("GET /capture")) {
//                            temp = temp.replace("GET /", "GET ws://192.168.99.9/");
//                            temp = temp.replace("localhost:9999", "192.168.99.9");
//                        }
//                        if (temp.contains("websocket")) {
//                            System.out.println(temp);
//                        }
//                        if (temp.contains("GET /sysinfo")) {
//                            temp = temp.replace("GET /", "GET ws://192.168.99.9/");
//                            temp = temp.replace("localhost:9999", "192.168.99.9");
//                            temp = temp.replace("Accept-Encoding: gzip, deflate, br", "Accept-Encoding:");
//                            System.out.println(temp);
//                        }
                        if (temp.contains("GET /js/") || temp.contains("GET /css/")) {
                            temp = temp.replace("Accept-Encoding: gzip, deflate", "Accept-Encoding:");
                            outToServer.write(temp.getBytes(), 0, bytes_read);
                        } else
                            outToServer.write(request, 0, bytes_read);
                        outToServer.flush();
                    }
                } catch (IOException ignored) {
                }
                try {
                    outToServer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            // current thread manages streams from server to client (DOWNLOAD)
            int bytes_read;
            try {
                while ((bytes_read = inFromServer.read(reply)) != -1) {
                    //TODO CREATE YOUR LOGIC HERE
                    String temp = new String(reply);
                    if (temp.contains("websocket")) {
                        System.out.println(temp);
                    }
                    byte[] subArray = Arrays.copyOfRange(reply, 0, 1000);
                    if (isUTF8(subArray)) {
                        temp = temp.replace("Ming Ji - Mini", "              ");
                        temp = temp.replace("Ming Ji Body Temp", " HiFace Body Temp ");
                        reply = temp.getBytes();
//                        System.out.println(new String(Arrays.copyOfRange(reply, 0, 1000)) + "\n\n");
                    }

                    outToClient.write(reply, 0, bytes_read);
                    outToClient.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (server != null)
                        server.close();
                    if (client != null)
                        client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            outToClient.close();
            sClient.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}