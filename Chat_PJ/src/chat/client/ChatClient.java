package chat.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {
    String chatName;
    Socket socket;

    DataInputStream dis;
    DataOutputStream dos;

    final String imagesDirectory = "images/";
    final String quitCommand = "/quit";
    final String renameCommand = "/rename";
    final String privateMessageCommand = "/to";

    public void connect(String serverIP, int portNo, String chatName) {
        try {
            socket = new Socket(serverIP, portNo);
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());

            this.chatName = chatName;
            send(chatName);

        } catch (IOException e) {
            System.out.println("서버에 연결할 수 없습니다: " + e.getMessage());
            System.exit(1);
        }
    }

    public void send(String message) {
        try {
            dos.writeUTF(message);
            dos.flush();
        } catch (IOException e) {
            System.out.println("메시지 전송 실패: " + e.getMessage());
        }
    }

    public void uploadImage(String imageName) {
        try {
            File file = new File(imagesDirectory + imageName);
            if (!file.exists()) {
                System.out.println("파일을 찾을 수 없습니다: " + imageName);
                return;
            }

            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            long fileSize = file.length();
            int bytesRead;

            dos.writeUTF("/img");
            dos.writeUTF(imageName);
            dos.writeLong(fileSize);

            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
            }
            dos.flush();
            fis.close();

            System.out.println("이미지 업로드 성공: " + imageName);
        } catch (IOException e) {
            System.out.println("이미지 업로드 실패: " + e.getMessage());
        }
    }
    public void downloadImage(String imageId) {
        new Thread(() -> {
            try {
                dos.writeUTF("/get " + imageId);

                String response = dis.readUTF();  // 텍스트 응답 수신
                if (response.startsWith("/imgdata")) {
                    int size = dis.readInt();    // 데이터 크기 수신
                    byte[] imageData = new byte[size];
                    dis.readFully(imageData);   // 실제 데이터 수신

                    // 이미지 저장
                    File file = new File(imagesDirectory);
                    if (!file.exists()) {
                        file.mkdirs();
                    }

                    FileOutputStream fos = new FileOutputStream(imagesDirectory + imageId + ".jpg");
                    fos.write(imageData);
                    fos.close();

                    System.out.println("이미지 다운로드 성공: " + imagesDirectory + imageId + ".jpg");
                } else {
                    System.out.println("이미지 다운로드 실패: " + response);
                }
            } catch (IOException e) {
                System.out.println("이미지 다운로드 실패: " + e.getMessage());
            }
        }).start();
    }



    public void receive(Scanner sc) {
        new Thread(() -> {
            try {
                while (true) {
                    String message = dis.readUTF();
                    if (message.startsWith("/imgdata")) {
                        downloadImage(message.split(" ")[1]);
                    } else {
                        System.out.println(message);
                        System.out.print("> ");
                    }
                }
            } catch (IOException e) {
                System.out.println("서버와의 연결이 끊어졌습니다.");
                quit();
                System.exit(0);
            }
        }).start();
    }

    public void quit() {
        try {
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.out.println("종료 중 오류 발생: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("이름 입력 : ");
        String chatName = sc.nextLine().trim();

        if (chatName.isEmpty()) {
            System.out.println("공백은 입력할 수 없습니다.");
            sc.close();
            return;
        }

        ChatClient chatClient = new ChatClient();
        chatClient.connect("localhost", 50005, chatName);
        chatClient.receive(sc);
        
        while (true) {
            System.out.print("> ");
            String message = sc.nextLine().trim();
            if (message.isEmpty())
                continue;

            if (message.startsWith("/img ")) {
                String imageName = message.substring(5).trim();
                if (imageName.isEmpty()) {  // 파일명이 없는 경우
                    System.out.println("사용법: /img [파일명]");
                    continue;
                }
                chatClient.uploadImage(imageName);
            } else if (message.startsWith("/get ")) {
                String imageId = message.substring(5).trim(); 
                if (imageId.isEmpty()) {  // 이미지 ID가 없는 경우
                    System.out.println("사용법: /get [이미지ID]");
                    continue;
                }
                chatClient.downloadImage(imageId);  // 비동기로 다운로드
            } else if (message.startsWith(chatClient.renameCommand) || message.startsWith(chatClient.privateMessageCommand)) {
                chatClient.send(message);
            } else if (message.equals(chatClient.quitCommand)) {
                break;
            } else {
                chatClient.send(message);
            }
        }


        chatClient.quit();
        sc.close();
    }
}
