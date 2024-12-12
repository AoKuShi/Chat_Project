package chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class ClientService {

    ChatServer chatServer;
    Socket socket;

    DataInputStream dis;
    DataOutputStream dos;

    String clientIP;
    String chatName;

    public ClientService(ChatServer chatServer, Socket socket) throws IOException {
        this.chatServer = chatServer;
        this.socket = socket;

        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());

        clientIP = socket.getInetAddress().getHostAddress();
        startChat();
    }

    private void startChat() throws IOException {
        boolean isUniqueName = false;
        while (!isUniqueName) {
            chatName = dis.readUTF();
            if (chatServer.checkNameUnique(chatName)) {
                isUniqueName = true;
                chatServer.addClient(this);
                send(chatName + "님이 참여했습니다.");
                receive();
            } else {
                send("중복된 이름입니다.");
            }
        }
    }

    public void receive() {
        new Thread(() -> {
            try {
                while (true) {
                    String message = dis.readUTF();
                    if (message.startsWith("/to")) {
                        privateMessage(message);
                    } else if (message.startsWith("/rename")) {
                        rename(message);
                    } else if (message.startsWith("/img")) {
                        handleImageUpload();
                    } else if (message.startsWith("/get")) {
                        handleImageDownload(message);
                    } else {
                        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                        chatServer.sendToAll(this, "[" + chatName + "](" + time + ") : " + message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                quit();
            }
        }).start();
    }

    private void handleImageUpload() throws IOException {
        String fileName = dis.readUTF();
        long fileSize = dis.readLong();
        byte[] imageData = new byte[(int) fileSize];
        dis.readFully(imageData);
        String imageId = UUID.randomUUID().toString();
        chatServer.storeImage(imageId, imageData);
        send("이미지 업로드 성공: " + imageId);
    }

    private void handleImageDownload(String message) throws IOException {
        String[] parts = message.split(" ");
        if (parts.length < 2) {
            send("사용법: /get [이미지ID]");
            return;
        }
        String imageId = parts[1];
        byte[] imageData = chatServer.getImage(imageId);
        if (imageData == null) {
            send("이미지를 찾을 수 없습니다.");
            return;
        }
        // 올바른 순서로 데이터 전송
        dos.writeUTF("/imgdata " + imageId);  // 텍스트 응답
        dos.writeInt(imageData.length);      // 데이터 크기 전송
        dos.write(imageData);                // 이미지 데이터 전송
        dos.flush();
    }


    private void rename(String message) {
        String[] parts = message.split(" ", 2);
        if (parts.length == 2) {
            String newName = parts[1].trim();
            if (newName.isEmpty()) {
                send("잘못 입력하셨습니다.");
                return;
            }

            try {
                if (chatServer.checkNameUnique(newName)) {
                    chatServer.changeName(this, newName);
                    this.chatName = newName;
                    send("이름이 " + chatName + "로 바뀌었습니다.");
                } else {
                    send("중복된 이름입니다.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            send("잘못 입력하셨습니다.");
        }
    }

    private void privateMessage(String message) {
        String[] parts = message.split(" ", 3);
        if (parts.length == 3) {
            chatServer.sendMessage(this, parts[1], parts[2]);
        } else {
            send("사용법: /to 닉네임 메시지");
        }
    }

    public void send(String message) {
        try {
            dos.writeUTF(message);
            dos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void quit() {
        chatServer.removeClient(this);
        close();
    }

    public void close() {
        try {
            dis.close();
            dos.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
