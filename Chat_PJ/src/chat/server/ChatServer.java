package chat.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {

    ServerSocket serverSocket;
    Map<String, ClientService> chatClientInfo = new ConcurrentHashMap<>();
    Map<String, String> imageStore = new ConcurrentHashMap<>();

    public void start(int portNo) {
        try {
            serverSocket = new ServerSocket(portNo);
            System.out.println("채팅 시작 -> " + InetAddress.getLocalHost() + ":" + portNo);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void connectClients() {
        Thread thread = new Thread(() -> {
            try {
                while (true) {
                    Socket socket = serverSocket.accept();
                    new ClientService(this, socket);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        try {
            serverSocket.close();
            System.out.println("채팅 종료");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void storeImage(String imageId, byte[] data) throws IOException {
        File directory = new File("images/");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        String fileName = imageId + ".jpg";
        FileOutputStream fos = new FileOutputStream(new File(directory, fileName));
        fos.write(data);
        fos.close();
        imageStore.put(imageId, fileName);
    }

    public synchronized byte[] getImage(String imageId) throws IOException {
        String fileName = imageStore.get(imageId);
        if (fileName == null) {
            return null;
        }
        File file = new File("images/", fileName);
        if (!file.exists()) {
            return null;
        }
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        return data;
    }

    public synchronized void sendToAll(ClientService sender, String message) {
        for (ClientService client : chatClientInfo.values()) {
            if (!client.equals(sender)) {
                client.send(message);
            }
        }
    }

    public synchronized boolean addClient(ClientService clientService) {
        if (chatClientInfo.containsKey(clientService.chatName)) {
            return false;
        }
        chatClientInfo.put(clientService.chatName, clientService);
        return true;
    }

    public synchronized void changeName(ClientService clientService, String newName) {
        chatClientInfo.remove(clientService.chatName);
        clientService.chatName = newName;
        chatClientInfo.put(newName, clientService);
    }

    public synchronized boolean checkNameUnique(String chatName) {
        return !chatClientInfo.containsKey(chatName);
    }

    public synchronized void sendMessage(ClientService sender, String recipientName, String message) {
        ClientService recipient = chatClientInfo.get(recipientName);
        if (recipient != null) {
            recipient.send(sender.chatName + "의 귓속말 : " + message);
        } else {
            sender.send("상대방이 없습니다.");
        }
    }

    public synchronized void removeClient(ClientService clientService) {
        chatClientInfo.remove(clientService.chatName);
    }

    public static void main(String[] args) {
        final int portNo = 50005;
        ChatServer chatServer = new ChatServer();
        chatServer.start(portNo);
        chatServer.connectClients();

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
