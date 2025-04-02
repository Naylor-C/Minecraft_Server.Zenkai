import java.net.*;
import java.io.*;

public class Server {
    private static final int PORT = 25565;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor Minecraft iniciado na porta " + PORT);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Novo cliente conectado: " + clientSocket.getInetAddress());
                
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    public void run() {
        try {
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
            
            // Implementar protocolo Minecraft aqui
            // Exemplo: handshake inicial
            int packetLength = in.readVarInt();
            int packetID = in.readVarInt();
            
            if (packetID == 0) { // Handshake
                int protocolVersion = in.readVarInt();
                String serverAddress = in.readUTF();
                int serverPort = in.readUnsignedShort();
                int nextState = in.readVarInt();
                
                System.out.println("Handshake recebido: " + serverAddress);
            }
            
            clientSocket.close();
        } catch (IOException e) {
            System.err.println("Erro com cliente: " + e.getMessage());
        }
    }
}
