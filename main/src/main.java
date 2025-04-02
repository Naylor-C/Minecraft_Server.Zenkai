import java.io.*;
import java.net.*;
import java.util.*;

public class BasicMinecraftServer {
    private static final int PORT = 25565;
    private static final int MAX_PLAYERS = 20;
    private static final String VERSION = "1.20.4";
    private static final int PROTOCOL_VERSION = 765;
    
    private static Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        System.out.println("Iniciando servidor Minecraft básico...");
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor iniciado na porta " + PORT);
            System.out.println("Versão do protocolo: " + PROTOCOL_VERSION + " (" + VERSION + ")");
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                if (clients.size() >= MAX_PLAYERS) {
                    rejectConnection(clientSocket, "Servidor cheio!");
                    continue;
                }
                
                System.out.println("Nova conexão de: " + clientSocket.getInetAddress());
                ClientHandler clientThread = new ClientHandler(clientSocket);
                clients.add(clientThread);
                new Thread(clientThread).start();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }
    
    private static void rejectConnection(Socket socket, String reason) throws IOException {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(byteOut);
        
        // Construir pacote de desconexão
        dataOut.writeUTF("{\"text\":\"" + reason + "\"}");
        
        // Escrever cabeçalho do pacote
        out.writeByte(0x00); // Packet ID for Disconnect
        writeVarInt(byteOut.size(), out);
        out.write(byteOut.toByteArray());
        
        socket.close();
    }
    
    public static void writeVarInt(int value, DataOutputStream out) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }
    
    public static void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Cliente desconectado. Jogadores online: " + clients.size());
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String username;
    
    public ClientHandler(Socket socket) {
        this.socket = socket;
    }
    
    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            
            // Handshake
            int packetLength = readVarInt(in);
            int packetId = readVarInt(in);
            
            if (packetId == 0x00) { // Handshake packet
                int protocolVersion = readVarInt(in);
                String serverAddress = readString(in);
                int serverPort = in.readUnsignedShort();
                int nextState = readVarInt(in);
                
                System.out.println("Handshake recebido - Versão: " + protocolVersion + 
                                 ", Endereço: " + serverAddress + 
                                 ", Próximo estado: " + nextState);
                
                if (nextState == 1) { // Status
                    handleStatusRequest();
                } else if (nextState == 2) { // Login
                    handleLogin();
                }
            }
        } catch (IOException e) {
            System.out.println("Erro com cliente: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar socket: " + e.getMessage());
            }
            BasicMinecraftServer.removeClient(this);
        }
    }
    
    private void handleStatusRequest() throws IOException {
        // Ler pacote de requisição de status (packet ID 0x00)
        int packetLength = readVarInt(in);
        int packetId = readVarInt(in);
        
        if (packetId != 0x00) {
            throw new IOException("Packet ID de status inválido");
        }
        
        // Enviar resposta de status
        String jsonResponse = String.format(
            "{\"version\":{\"name\":\"%s\",\"protocol\":%d},\"players\":{\"max\":%d,\"online\":%d,\"sample\":[]},\"description\":{\"text\":\"Servidor customizado em Java\"}}",
            BasicMinecraftServer.VERSION,
            BasicMinecraftServer.PROTOCOL_VERSION,
            BasicMinecraftServer.MAX_PLAYERS,
            BasicMinecraftServer.clients.size()
        );
        
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(byteOut);
        dataOut.writeUTF(jsonResponse);
        
        // Enviar pacote de resposta (0x00)
        out.writeByte(0x00); // Packet ID
        BasicMinecraftServer.writeVarInt(byteOut.size(), out);
        out.write(byteOut.toByteArray());
        
        // Enviar pacote de ping (0x01)
        long payload = System.currentTimeMillis();
        out.writeByte(0x01); // Packet ID
        out.writeLong(payload);
    }
    
    private void handleLogin() throws IOException {
        // Ler pacote de login (packet ID 0x00)
        int packetLength = readVarInt(in);
        int packetId = readVarInt(in);
        
        if (packetId != 0x00) {
            throw new IOException("Packet ID de login inválido");
        }
        
        username = readString(in);
        System.out.println("Tentativa de login: " + username);
        
        // Aqui você implementaria a lógica de autenticação real
        
        // Enviar pacote de login bem-sucedido (simplificado)
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(byteOut);
        dataOut.writeUTF(username); // UUID seria usado em um servidor real
        dataOut.writeUTF(username);
        
        out.writeByte(0x02); // Packet ID for Login Success
        BasicMinecraftServer.writeVarInt(byteOut.size(), out);
        out.write(byteOut.toByteArray());
    }
    
    private int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int length = 0;
        byte current;
        
        while (true) {
            current = in.readByte();
            value |= (current & 0x7F) << (length * 7);
            
            length++;
            if (length > 5) {
                throw new IOException("VarInt muito grande");
            }
            
            if ((current & 0x80) != 0x80) {
                break;
            }
        }
        return value;
    }
    
    private String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, "UTF-8");
    }
}