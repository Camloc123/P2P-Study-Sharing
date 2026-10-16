package server;

import p2p.FileHashUtil;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Máy chủ danh bạ trung tâm (Directory Server).
 * Lắng nghe tại cổng 5000, quản lý danh sách Peer online và danh bạ file chia sẻ.
 * Nhận và xử lý các lệnh: CONNECT, SHARE, UNSHARE, SEARCH, DISCONNECT.
 */
public class DirectoryServer {

    public static final int DEFAULT_PORT = 5000;

    // Model lưu thông tin Peer
    public static class PeerInfo {
        private final String peerId;
        private final String ipAddress;
        private final int p2pPort;
        private final String connectedAt;
        private final Socket socket;
        private final PrintWriter out;
        private final BufferedReader in;

        public PeerInfo(String peerId, String ipAddress, int p2pPort, Socket socket, PrintWriter out, BufferedReader in) {
            this.peerId = peerId;
            this.ipAddress = ipAddress;
            this.p2pPort = p2pPort;
            this.connectedAt = new SimpleDateFormat("HH:mm:ss dd/MM").format(new Date());
            this.socket = socket;
            this.out = out;
            this.in = in;
        }

        public String getPeerId() { return peerId; }
        public String getIpAddress() { return ipAddress; }
        public int getP2pPort() { return p2pPort; }
        public String getConnectedAt() { return connectedAt; }
        public Socket getSocket() { return socket; }
        public PrintWriter getOut() { return out; }
    }

    // Model lưu thông tin File trong danh bạ
    public static class SharedFileInfo {
        private final String fileName;
        private final long fileSize;
        private final String fileHash;
        private final String ownerPeerId;
        private final String ownerIp;
        private final int ownerP2pPort;

        public SharedFileInfo(String fileName, long fileSize, String fileHash, String ownerPeerId, String ownerIp, int ownerP2pPort) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.fileHash = fileHash;
            this.ownerPeerId = ownerPeerId;
            this.ownerIp = ownerIp;
            this.ownerP2pPort = ownerP2pPort;
        }

        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
        public String getFileHash() { return fileHash; }
        public String getOwnerPeerId() { return ownerPeerId; }
        public String getOwnerIp() { return ownerIp; }
        public int getOwnerP2pPort() { return ownerP2pPort; }
    }

    // Interface callback cập nhật giao diện ServerGUI
    public interface ServerObserver {
        void onLog(String message);
        void onPeerConnected(PeerInfo peer);
        void onPeerDisconnected(String peerId);
        void onFileShared(SharedFileInfo file);
        void onFileUnshared(String fileHash, String ownerPeerId);
        void onServerStatusChanged(boolean running, int port);
    }

    private final int port;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private final Map<String, PeerInfo> activePeers = new ConcurrentHashMap<>();
    // Key: fileHash + "@" + ownerPeerId -> FileInfo
    private final Map<String, SharedFileInfo> fileDirectory = new ConcurrentHashMap<>();
    private final List<ServerObserver> observers = new CopyOnWriteArrayList<>();
    private ExecutorService threadPool;

    public DirectoryServer(int port) {
        this.port = port;
    }

    public void addObserver(ServerObserver obs) {
        observers.add(obs);
    }

    public synchronized void startServer() throws IOException {
        if (isRunning) return;
        serverSocket = new ServerSocket(port);
        isRunning = true;
        threadPool = Executors.newCachedThreadPool();

        notifyLog("[HỆ THỐNG] Directory Server đã khởi động thành công trên cổng: " + port);
        notifyStatus(true, port);

        // Luồng chấp nhận kết nối từ các Peer
        new Thread(() -> {
            while (isRunning && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(new ClientHandler(clientSocket));
                } catch (IOException e) {
                    if (!isRunning) break;
                    notifyLog("[CẢNH BÁO] Lỗi chấp nhận kết nối: " + e.getMessage());
                }
            }
        }, "DirectoryServer-Acceptor").start();
    }

    public synchronized void stopServer() {
        if (!isRunning) return;
        isRunning = false;
        try {
            // Đóng tất cả kết nối peer
            for (PeerInfo peer : activePeers.values()) {
                try {
                    peer.getOut().println("KICKED|Server đang tắt");
                    peer.getSocket().close();
                } catch (Exception ignored) {}
            }
            activePeers.clear();
            fileDirectory.clear();

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (threadPool != null) {
                threadPool.shutdownNow();
            }
        } catch (IOException e) {
            notifyLog("[LỖI] Đóng Server thất bại: " + e.getMessage());
        }
        notifyLog("[HỆ THỐNG] Directory Server đã dừng hoạt động.");
        notifyStatus(false, port);
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getPort() {
        return port;
    }

    public Map<String, PeerInfo> getActivePeers() {
        return Collections.unmodifiableMap(activePeers);
    }

    public Map<String, SharedFileInfo> getFileDirectory() {
        return Collections.unmodifiableMap(fileDirectory);
    }

    /**
     * Admin Kick một Peer ra khỏi hệ thống
     */
    public synchronized boolean kickPeer(String peerId, String reason) {
        PeerInfo peer = activePeers.get(peerId);
        if (peer == null) return false;
        try {
            peer.getOut().println("KICKED|" + (reason == null ? "Bị Admin ngắt kết nối" : reason));
            peer.getSocket().close();
        } catch (Exception ignored) {}

        removePeer(peerId);
        notifyLog("[ADMIN] Đã Kick Peer [" + peerId + "]. Lý do: " + reason);
        return true;
    }

    /**
     * Admin Xóa tài liệu khỏi danh bạ trung tâm
     */
    public synchronized boolean deleteFileFromDirectory(String fileHash, String ownerPeerId) {
        String key = fileHash + "@" + ownerPeerId;
        SharedFileInfo removed = fileDirectory.remove(key);
        if (removed != null) {
            notifyFileUnshared(fileHash, ownerPeerId);
            notifyLog("[ADMIN] Đã xóa tài liệu [" + removed.getFileName() + "] của Peer [" + ownerPeerId + "] khỏi danh bạ.");
            // Thông báo cho peer đó biết file bị unshare
            PeerInfo peer = activePeers.get(ownerPeerId);
            if (peer != null) {
                peer.getOut().println("FILE_REMOVED_BY_ADMIN|" + fileHash);
            }
            return true;
        }
        return false;
    }

    private synchronized void removePeer(String peerId) {
        PeerInfo removedPeer = activePeers.remove(peerId);
        if (removedPeer != null) {
            // Gỡ toàn bộ file của peer này
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, SharedFileInfo> entry : fileDirectory.entrySet()) {
                if (entry.getValue().getOwnerPeerId().equals(peerId)) {
                    toRemove.add(entry.getKey());
                    notifyFileUnshared(entry.getValue().getFileHash(), peerId);
                }
            }
            for (String key : toRemove) {
                fileDirectory.remove(key);
            }
            notifyPeerDisconnected(peerId);
            notifyLog("[NGẮT KẾT NỐI] Peer [" + peerId + "] đã rời khỏi hệ thống. Đã gỡ " + toRemove.size() + " tài liệu liên quan.");
        }
    }

    private void notifyLog(String msg) {
        for (ServerObserver obs : observers) {
            obs.onLog(msg);
        }
    }

    private void notifyStatus(boolean running, int port) {
        for (ServerObserver obs : observers) {
            obs.onServerStatusChanged(running, port);
        }
    }

    private void notifyPeerConnected(PeerInfo peer) {
        for (ServerObserver obs : observers) {
            obs.onPeerConnected(peer);
        }
    }

    private void notifyPeerDisconnected(String peerId) {
        for (ServerObserver obs : observers) {
            obs.onPeerDisconnected(peerId);
        }
    }

    private void notifyFileShared(SharedFileInfo file) {
        for (ServerObserver obs : observers) {
            obs.onFileShared(file);
        }
    }

    private void notifyFileUnshared(String fileHash, String ownerPeerId) {
        for (ServerObserver obs : observers) {
            obs.onFileUnshared(fileHash, ownerPeerId);
        }
    }

    /**
     * Handler xử lý một kết nối Peer đến Server
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private String clientPeerId = null;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            String remoteIp = socket.getInetAddress().getHostAddress();
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)
            ) {
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split("\\|", -1);
                    String command = parts[0].toUpperCase();

                    switch (command) {
                        case "CONNECT": {
                            // Lệnh: CONNECT|<peerId>|<p2pPort>
                            if (parts.length >= 3) {
                                String reqPeerId = parts[1].trim();
                                int p2pPort = Integer.parseInt(parts[2].trim());

                                if (activePeers.containsKey(reqPeerId)) {
                                    out.println("ERROR|Peer ID đã tồn tại! Vui lòng chọn tên khác.");
                                    return;
                                }

                                clientPeerId = reqPeerId;
                                PeerInfo peer = new PeerInfo(clientPeerId, remoteIp, p2pPort, socket, out, in);
                                activePeers.put(clientPeerId, peer);

                                notifyPeerConnected(peer);
                                notifyLog("[KẾT NỐI MỚI] Peer [" + clientPeerId + "] kết nối từ " + remoteIp + " (P2P Port: " + p2pPort + ")");
                                out.println("CONNECT_OK|" + clientPeerId);
                            } else {
                                out.println("ERROR|Cú pháp CONNECT không hợp lệ.");
                            }
                            break;
                        }

                        case "SHARE": {
                            // Lệnh: SHARE|<fileName>|<fileSize>|<fileHash>
                            if (clientPeerId == null) {
                                out.println("ERROR|Chưa đăng ký kết nối (chưa CONNECT).");
                                break;
                            }
                            if (parts.length >= 4) {
                                String fileName = parts[1].trim();
                                long fileSize = Long.parseLong(parts[2].trim());
                                String fileHash = parts[3].trim();

                                PeerInfo peer = activePeers.get(clientPeerId);
                                if (peer != null) {
                                    SharedFileInfo fileInfo = new SharedFileInfo(
                                        fileName, fileSize, fileHash,
                                        clientPeerId, peer.getIpAddress(), peer.getP2pPort()
                                    );
                                    String key = fileHash + "@" + clientPeerId;
                                    fileDirectory.put(key, fileInfo);

                                    notifyFileShared(fileInfo);
                                    notifyLog("[CHIA SẺ] Peer [" + clientPeerId + "] đã đăng tải file [" + fileName + "] (" + FileHashUtil.formatFileSize(fileSize) + ")");
                                    out.println("SHARE_OK|" + fileHash);
                                }
                            }
                            break;
                        }

                        case "UNSHARE": {
                            // Lệnh: UNSHARE|<fileHash>
                            if (clientPeerId != null && parts.length >= 2) {
                                String fileHash = parts[1].trim();
                                String key = fileHash + "@" + clientPeerId;
                                SharedFileInfo removed = fileDirectory.remove(key);
                                if (removed != null) {
                                    notifyFileUnshared(fileHash, clientPeerId);
                                    notifyLog("[HỦY CHIA SẺ] Peer [" + clientPeerId + "] đã hủy file [" + removed.getFileName() + "]");
                                    out.println("UNSHARE_OK|" + fileHash);
                                } else {
                                    out.println("ERROR|Không tìm thấy file để hủy.");
                                }
                            }
                            break;
                        }

                        case "SEARCH": {
                            // Lệnh: SEARCH|<keyword>
                            String keyword = (parts.length >= 2) ? parts[1].trim().toLowerCase() : "";
                            List<SharedFileInfo> results = new ArrayList<>();
                            for (SharedFileInfo info : fileDirectory.values()) {
                                if (keyword.isEmpty() || info.getFileName().toLowerCase().contains(keyword)) {
                                    results.add(info);
                                }
                            }
                            // Phản hồi số lượng kết quả trước
                            out.println("SEARCH_START|" + results.size());
                            for (SharedFileInfo info : results) {
                                // ITEM|fileName|fileSize|fileHash|ownerPeerId|ownerIp|ownerP2pPort
                                out.println("ITEM|" + info.getFileName() + "|" + info.getFileSize() + "|" +
                                            info.getFileHash() + "|" + info.getOwnerPeerId() + "|" +
                                            info.getOwnerIp() + "|" + info.getOwnerP2pPort());
                            }
                            out.println("SEARCH_END");
                            notifyLog("[TÌM KIẾM] Peer [" + clientPeerId + "] tìm từ khóa: \"" + keyword + "\" -> " + results.size() + " kết quả.");
                            break;
                        }

                        case "LIST_ALL": {
                            // Lấy tất cả file hiện có
                            out.println("SEARCH_START|" + fileDirectory.size());
                            for (SharedFileInfo info : fileDirectory.values()) {
                                out.println("ITEM|" + info.getFileName() + "|" + info.getFileSize() + "|" +
                                            info.getFileHash() + "|" + info.getOwnerPeerId() + "|" +
                                            info.getOwnerIp() + "|" + info.getOwnerP2pPort());
                            }
                            out.println("SEARCH_END");
                            break;
                        }

                        case "DISCONNECT": {
                            out.println("DISCONNECT_OK");
                            return;
                        }

                        default:
                            out.println("ERROR|Lệnh không được hỗ trợ: " + command);
                            break;
                    }
                }
            } catch (IOException e) {
                // Client đột ngột ngắt kết nối
            } finally {
                if (clientPeerId != null) {
                    removePeer(clientPeerId);
                }
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
