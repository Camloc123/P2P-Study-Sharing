package p2p;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * P2P File Node (P2PFileNode.java)
 * Bao gồm cả 3 vai trò:
 * 1. Mini-Server (Người đăng): ServerSocket chạy ngầm ở cổng riêng, đọc file bắn qua cho máy khác.
 * 2. Downloader (Người lấy): Kết nối thẳng tới IP:Port của peer để kéo luồng byte về, đẩy phần trăm 0%->100%.
 * 3. Kiểm tra lỗi (Data Integrity): So khớp mã băm SHA-256 đảm bảo file lớn không hỏng dữ liệu.
 */
public class P2PFileNode extends JFrame {

    // Theme màu sắc tương phản cao (High-Contrast Slate)
    private static final Color BG_DARK = new Color(15, 23, 42);          // Slate 900
    private static final Color PANEL_BG = new Color(30, 41, 59);         // Slate 800
    private static final Color CARD_BG = new Color(51, 65, 85);          // Slate 700
    private static final Color BORDER_COLOR = new Color(100, 116, 139);  // Slate 500
    private static final Color ACCENT_CYAN = new Color(56, 189, 248);    // Sky 400
    private static final Color ACCENT_BLUE = new Color(37, 99, 235);     // Blue 600
    private static final Color ACCENT_GREEN = new Color(34, 197, 94);    // Green 500
    private static final Color ACCENT_RED = new Color(239, 68, 68);      // Red 500
    private static final Color ACCENT_PURPLE = new Color(147, 51, 234);  // Purple 600
    private static final Color TEXT_MAIN = new Color(248, 250, 252);     // Pure White
    private static final Color TEXT_MUTED = new Color(203, 213, 225);    // Slate 300
    private static final Color TABLE_HEADER_BG = new Color(15, 23, 42);
    private static final Color TABLE_ROW_ALT = new Color(30, 41, 59);
    private static final Color TABLE_ROW_MAIN = new Color(39, 54, 76);
    private static final Color TABLE_SELECTED = new Color(14, 116, 144);

    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 16);
    private static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_MONO = new Font("Consolas", Font.BOLD, 12);

    // Dữ liệu nội bộ
    public static class LocalSharedFile {
        public String fileName;
        public long fileSize;
        public String fileHash;
        public File localFile;

        public LocalSharedFile(String fileName, long fileSize, String fileHash, File localFile) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.fileHash = fileHash;
            this.localFile = localFile;
        }
    }

    // Danh sách file mình đang chia sẻ (Key: fileHash)
    private final Map<String, LocalSharedFile> mySharedFiles = new ConcurrentHashMap<>();
    private final File downloadFolder = new File("downloads");

    // Socket kết nối Directory Server
    private Socket serverSocket;
    private PrintWriter serverOut;
    private BufferedReader serverIn;
    private boolean isConnectedToServer = false;

    // Mini-Server Socket
    private ServerSocket miniServerSocket;
    private int miniServerPort = 6001;
    private boolean isMiniServerRunning = false;
    private final ExecutorService uploadPool = Executors.newCachedThreadPool();

    // UI Components
    private JTextField txtPeerId;
    private JTextField txtMiniPort;
    private JTextField txtServerHost;
    private JTextField txtServerPort;
    private JButton btnConnectServer;
    private JLabel lblStatus;

    // Tab Tìm kiếm & Tải file
    private JTextField txtSearch;
    private DefaultTableModel searchTableModel;
    private JTable tblSearchResults;
    private JProgressBar prgDownload;
    private JLabel lblDownloadStatus;
    private JLabel lblDownloadSpeed;

    // Tab File chia sẻ
    private DefaultTableModel myFilesTableModel;
    private JTable tblMyFiles;

    // Tab Log
    private JTextArea txtLog;

    public P2PFileNode() {
        setTitle("P2P File Sharing Node - Máy Trạm Đa Năng");
        setSize(1100, 720);
        setMinimumSize(new Dimension(920, 620));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        if (!downloadFolder.exists()) {
            downloadFolder.mkdirs();
        }

        int randomId = (int) (Math.random() * 900) + 100;
        int defaultPort = 6000 + (int) (Math.random() * 100);

        initComponents("Peer_" + randomId, defaultPort);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnectFromServer();
                stopMiniServer();
            }
        });
    }

    private void initComponents(String defaultPeerId, int defaultPort) {
        setLayout(new BorderLayout(10, 10));

        // 1. Top Bar
        add(createTopBar(defaultPeerId, defaultPort), BorderLayout.NORTH);

        // 2. Tabbed Pane tùy chỉnh giao diện sắc nét
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(PANEL_BG);
        tabbedPane.setForeground(Color.WHITE);
        tabbedPane.setFont(FONT_HEADER);

        // Custom UI cho Tab để chữ luôn sáng rõ trên mọi phiên bản Windows
        tabbedPane.setUI(new BasicTabbedPaneUI() {
            @Override
            protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (isSelected) {
                    g2.setColor(new Color(2, 132, 199)); // Sky Blue sáng
                } else {
                    g2.setColor(new Color(30, 41, 59));  // Dark Slate
                }
                g2.fillRect(x, y, w, h);
                g2.setColor(BORDER_COLOR);
                g2.drawRect(x, y, w, h);
                g2.dispose();
            }

            @Override
            protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex, String title, Rectangle textRect, boolean isSelected) {
                g.setFont(new Font("Segoe UI", Font.BOLD, 13));
                g.setColor(isSelected ? Color.WHITE : new Color(226, 232, 240));
                g.drawString(title, textRect.x, textRect.y + metrics.getAscent());
            }

            @Override
            protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) {}
        });

        tabbedPane.addTab("1. TÌM KIẾM & TẢI FILE (DOWNLOADER)", createSearchDownloadPanel());
        tabbedPane.addTab("2. KHO FILE TÔI CHIA SẺ (MINI-SERVER)", createMyFilesPanel());
        tabbedPane.addTab("3. NHẬT KÝ & HOẠT ĐỘNG P2P", createLogPanel());

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createTopBar(String defaultPeerId, int defaultPort) {
        JPanel panel = new JPanel(new BorderLayout(12, 8));
        panel.setBackground(PANEL_BG);
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        JPanel rowInputs = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        rowInputs.setOpaque(false);

        JLabel lblTitle = new JLabel("P2P NODE: ");
        lblTitle.setFont(FONT_TITLE);
        lblTitle.setForeground(ACCENT_CYAN);
        rowInputs.add(lblTitle);

        rowInputs.add(createLabel("Tên Máy:"));
        txtPeerId = createTextField(defaultPeerId, 8);
        rowInputs.add(txtPeerId);

        rowInputs.add(createLabel("Cổng Mini-Server:"));
        txtMiniPort = createTextField(String.valueOf(defaultPort), 5);
        rowInputs.add(txtMiniPort);

        rowInputs.add(createLabel("Server Host:"));
        txtServerHost = createTextField("127.0.0.1", 9);
        rowInputs.add(txtServerHost);

        rowInputs.add(createLabel("Port:"));
        txtServerPort = createTextField("5000", 4);
        rowInputs.add(txtServerPort);

        btnConnectServer = createCustomButton("Kết Nối Server", ACCENT_GREEN, Color.WHITE);
        btnConnectServer.addActionListener(e -> toggleConnection());
        rowInputs.add(btnConnectServer);

        panel.add(rowInputs, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        right.setOpaque(false);
        lblStatus = new JLabel("○ Chưa kết nối Server");
        lblStatus.setFont(FONT_HEADER);
        lblStatus.setForeground(ACCENT_RED);
        right.add(lblStatus);

        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private JPanel createSearchDownloadPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(10, 12, 10, 12));

        // Thanh tìm kiếm
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        searchBar.setBackground(PANEL_BG);
        searchBar.setBorder(new LineBorder(BORDER_COLOR, 1, true));

        JLabel lblSearch = new JLabel("Nhập từ khóa tài liệu:");
        lblSearch.setFont(FONT_HEADER);
        lblSearch.setForeground(TEXT_MAIN);

        txtSearch = createTextField("", 25);
        txtSearch.addActionListener(e -> performSearch());

        JButton btnSearch = createCustomButton("Tìm Kiếm", ACCENT_BLUE, Color.WHITE);
        btnSearch.addActionListener(e -> performSearch());

        JButton btnListAll = createCustomButton("Tất Cả Tài Liệu", new Color(14, 116, 144), Color.WHITE);
        btnListAll.addActionListener(e -> requestListAll());

        searchBar.add(lblSearch);
        searchBar.add(txtSearch);
        searchBar.add(btnSearch);
        searchBar.add(btnListAll);

        panel.add(searchBar, BorderLayout.NORTH);

        // Bảng kết quả tìm kiếm
        String[] columns = {"Tên Tài Liệu", "Dung Lượng", "Mã Băm SHA-256", "Người Đăng (Peer ID)", "IP:Port Nguồn"};
        searchTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        tblSearchResults = new JTable(searchTableModel);
        setupTableStyle(tblSearchResults);

        tblSearchResults.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                setFont(FONT_MONO);
                setForeground(isSelected ? Color.WHITE : ACCENT_CYAN);
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(tblSearchResults);
        scrollPane.getViewport().setBackground(PANEL_BG);
        scrollPane.setBorder(new LineBorder(BORDER_COLOR, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Khung Download & Tiến trình
        JPanel downloadBox = new JPanel(new BorderLayout(8, 8));
        downloadBox.setBackground(PANEL_BG);
        downloadBox.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_COLOR, 1, true),
            new EmptyBorder(10, 14, 10, 14)
        ));

        JPanel topAction = new JPanel(new BorderLayout(10, 4));
        topAction.setOpaque(false);

        JButton btnDownload = createCustomButton("[>] Bắt Đầu Tải Về (Direct Download)", ACCENT_PURPLE, Color.WHITE);
        btnDownload.addActionListener(e -> startDownloadSelectedFile());

        JButton btnOpenFolder = createCustomButton("Mở Thư Mục Downloads", CARD_BG, Color.WHITE);
        btnOpenFolder.addActionListener(e -> openDownloadFolder());

        JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionBtns.setOpaque(false);
        actionBtns.add(btnDownload);
        actionBtns.add(btnOpenFolder);

        lblDownloadSpeed = new JLabel("Tốc độ: 0 KB/s");
        lblDownloadSpeed.setFont(FONT_HEADER);
        lblDownloadSpeed.setForeground(ACCENT_CYAN);

        topAction.add(actionBtns, BorderLayout.WEST);
        topAction.add(lblDownloadSpeed, BorderLayout.EAST);

        prgDownload = new JProgressBar(0, 100);
        prgDownload.setStringPainted(true);
        prgDownload.setFont(FONT_HEADER);
        prgDownload.setForeground(ACCENT_GREEN);
        prgDownload.setBackground(CARD_BG);
        prgDownload.setPreferredSize(new Dimension(100, 28));

        lblDownloadStatus = new JLabel("Sẵn sàng tải file...");
        lblDownloadStatus.setFont(FONT_BODY);
        lblDownloadStatus.setForeground(TEXT_MUTED);

        downloadBox.add(topAction, BorderLayout.NORTH);
        downloadBox.add(prgDownload, BorderLayout.CENTER);
        downloadBox.add(lblDownloadStatus, BorderLayout.SOUTH);

        panel.add(downloadBox, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createMyFilesPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel topTool = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        topTool.setBackground(PANEL_BG);
        topTool.setBorder(new LineBorder(BORDER_COLOR, 1, true));

        JButton btnAddFile = createCustomButton("+ Thêm File Chia Sẻ (Đăng Lên Server)", ACCENT_GREEN, Color.WHITE);
        btnAddFile.addActionListener(e -> chooseAndShareFile());

        JButton btnRemoveFile = createCustomButton("- Hủy Chia Sẻ File", ACCENT_RED, Color.WHITE);
        btnRemoveFile.addActionListener(e -> unshareSelectedFile());

        JLabel lblNotice = new JLabel("Ghi chú: File được băm SHA-256 để máy khác tải về xác thực tính toàn vẹn 100%.");
        lblNotice.setFont(FONT_BODY);
        lblNotice.setForeground(TEXT_MUTED);

        topTool.add(btnAddFile);
        topTool.add(btnRemoveFile);
        topTool.add(lblNotice);

        panel.add(topTool, BorderLayout.NORTH);

        String[] columns = {"Tên Tài Liệu", "Dung Lượng", "Mã Băm SHA-256", "Đường Dẫn Thực Trên Ổ Cứng"};
        myFilesTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        tblMyFiles = new JTable(myFilesTableModel);
        setupTableStyle(tblMyFiles);

        tblMyFiles.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                setFont(FONT_MONO);
                setForeground(isSelected ? Color.WHITE : ACCENT_GREEN);
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(tblMyFiles);
        scrollPane.getViewport().setBackground(PANEL_BG);
        scrollPane.setBorder(new LineBorder(BORDER_COLOR, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(10, 12, 10, 12));

        txtLog = new JTextArea();
        txtLog.setBackground(new Color(10, 15, 30));
        txtLog.setForeground(new Color(241, 245, 249));
        txtLog.setFont(FONT_MONO);
        txtLog.setEditable(false);
        txtLog.setLineWrap(true);
        txtLog.setWrapStyleWord(true);
        txtLog.setMargin(new Insets(8, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(txtLog);
        scroll.setBorder(new LineBorder(BORDER_COLOR, 1));
        panel.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        bottom.setOpaque(false);
        JButton btnClear = createCustomButton("Xóa Nhật Ký", CARD_BG, Color.WHITE);
        btnClear.addActionListener(e -> txtLog.setText(""));
        bottom.add(btnClear);

        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    // ================= MINI-SERVER (NGƯỜI ĐĂNG) =================

    private void startMiniServer(int port) {
        if (isMiniServerRunning) return;
        try {
            miniServerSocket = new ServerSocket(port);
            miniServerPort = port;
            isMiniServerRunning = true;
            log("[MINI-SERVER] Đã khởi động ServerSocket chạy ngầm tại cổng P2P: " + miniServerPort);

            new Thread(() -> {
                while (isMiniServerRunning && !miniServerSocket.isClosed()) {
                    try {
                        Socket peerSocket = miniServerSocket.accept();
                        uploadPool.submit(new PeerUploadHandler(peerSocket));
                    } catch (IOException e) {
                        if (!isMiniServerRunning) break;
                        log("[MINI-SERVER LỖI] " + e.getMessage());
                    }
                }
            }, "MiniServer-Acceptor").start();

        } catch (IOException e) {
            log("[MINI-SERVER LỖI] Không thể mở cổng P2P " + port + ": " + e.getMessage());
        }
    }

    private void stopMiniServer() {
        isMiniServerRunning = false;
        try {
            if (miniServerSocket != null && !miniServerSocket.isClosed()) {
                miniServerSocket.close();
            }
        } catch (IOException ignored) {}
    }

    private class PeerUploadHandler implements Runnable {
        private final Socket socket;

        public PeerUploadHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            String remoteIp = socket.getInetAddress().getHostAddress();
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                OutputStream rawOut = socket.getOutputStream()
            ) {
                String request = in.readLine();
                if (request == null) return;

                String[] parts = request.trim().split("\\|", -1);
                if (parts.length >= 2 && "DOWNLOAD".equalsIgnoreCase(parts[0])) {
                    String reqHash = parts[1].trim();
                    LocalSharedFile fileEntry = mySharedFiles.get(reqHash);

                    if (fileEntry == null || !fileEntry.localFile.exists()) {
                        String errMsg = "ERR|File không tồn tại hoặc đã ngừng chia sẻ\n";
                        rawOut.write(errMsg.getBytes("UTF-8"));
                        rawOut.flush();
                        log("[UPLOAD THẤT BẠI] Máy " + remoteIp + " yêu cầu file không tồn tại.");
                        return;
                    }

                    String header = "OK|" + fileEntry.fileName + "|" + fileEntry.fileSize + "|" + fileEntry.fileHash + "\n";
                    rawOut.write(header.getBytes("UTF-8"));
                    rawOut.flush();

                    log("[BẮT ĐẦU UPLOAD] Đang đọc file [" + fileEntry.fileName + "] từ ổ cứng bắn sang máy " + remoteIp + "...");

                    byte[] buffer = new byte[64 * 1024];
                    try (FileInputStream fis = new FileInputStream(fileEntry.localFile)) {
                        int bytesRead;
                        long totalSent = 0;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            rawOut.write(buffer, 0, bytesRead);
                            totalSent += bytesRead;
                        }
                        rawOut.flush();
                        log("[UPLOAD HOÀN TẤT] Đã truyền xong [" + fileEntry.fileName + "] (" +
                            FileHashUtil.formatFileSize(totalSent) + ") sang máy " + remoteIp);
                    }
                }
            } catch (IOException e) {
                log("[UPLOAD BỊ GIÁN ĐOẠN] " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    // ================= DOWNLOADER (NGƯỜI LẤY) =================

    private void startDownloadSelectedFile() {
        int selectedRow = tblSearchResults.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một file trong bảng kết quả tìm kiếm!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String fileName = (String) searchTableModel.getValueAt(selectedRow, 0);
        String expectedHash = (String) searchTableModel.getValueAt(selectedRow, 2);
        String ownerId = (String) searchTableModel.getValueAt(selectedRow, 3);
        String ipPortStr = (String) searchTableModel.getValueAt(selectedRow, 4);

        if (ownerId.equals(txtPeerId.getText().trim())) {
            JOptionPane.showMessageDialog(this, "Đây là file do chính bạn chia sẻ!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] hostParts = ipPortStr.split(":");
        if (hostParts.length < 2) {
            JOptionPane.showMessageDialog(this, "Địa chỉ Peer không hợp lệ: " + ipPortStr, "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String targetIp = hostParts[0];
        int targetPort = Integer.parseInt(hostParts[1]);

        new Thread(() -> downloadWorker(fileName, expectedHash, targetIp, targetPort), "DownloaderThread").start();
    }

    private void downloadWorker(String fileName, String expectedHash, String targetIp, int targetPort) {
        SwingUtilities.invokeLater(() -> {
            prgDownload.setValue(0);
            lblDownloadStatus.setText("Đang kết nối tới máy " + targetIp + ":" + targetPort + "...");
            lblDownloadStatus.setForeground(ACCENT_CYAN);
            lblDownloadSpeed.setText("Tốc độ: 0 KB/s");
        });

        File destinationFile = new File(downloadFolder, fileName);
        long startTime = System.currentTimeMillis();

        try (Socket socket = new Socket(targetIp, targetPort)) {
            socket.setSoTimeout(15000);
            OutputStream rawOut = socket.getOutputStream();
            InputStream rawIn = socket.getInputStream();

            String req = "DOWNLOAD|" + expectedHash + "\n";
            rawOut.write(req.getBytes("UTF-8"));
            rawOut.flush();

            ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
            int b;
            while ((b = rawIn.read()) != -1) {
                if (b == '\n') break;
                headerBuffer.write(b);
            }
            String header = headerBuffer.toString("UTF-8").trim();

            if (!header.startsWith("OK|")) {
                SwingUtilities.invokeLater(() -> {
                    lblDownloadStatus.setText("Lỗi máy bạn từ chối: " + header);
                    lblDownloadStatus.setForeground(ACCENT_RED);
                });
                return;
            }

            String[] headerParts = header.split("\\|", -1);
            long totalSize = Long.parseLong(headerParts[2]);

            log("[DOWNLOAD] Đã kết nối! Bắt đầu kéo luồng byte file [" + fileName + "] (" + FileHashUtil.formatFileSize(totalSize) + ")");

            try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
                byte[] buffer = new byte[64 * 1024];
                int bytesRead;
                long totalDownloaded = 0;
                long lastSpeedCalcTime = startTime;
                long bytesSinceLastSpeedCalc = 0;

                while (totalDownloaded < totalSize && (bytesRead = rawIn.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalDownloaded += bytesRead;
                    bytesSinceLastSpeedCalc += bytesRead;

                    int percent = (int) ((totalDownloaded * 100) / totalSize);

                    long now = System.currentTimeMillis();
                    if (now - lastSpeedCalcTime >= 350) {
                        double speedKb = (bytesSinceLastSpeedCalc / 1024.0) / ((now - lastSpeedCalcTime) / 1000.0);
                        lastSpeedCalcTime = now;
                        bytesSinceLastSpeedCalc = 0;

                        long finalDownloaded = totalDownloaded;
                        SwingUtilities.invokeLater(() -> {
                            prgDownload.setValue(percent);
                            lblDownloadSpeed.setText(String.format("Tốc độ: %.1f KB/s", speedKb));
                            lblDownloadStatus.setText(String.format("Đang tải: %s / %s (%d%%)",
                                FileHashUtil.formatFileSize(finalDownloaded),
                                FileHashUtil.formatFileSize(totalSize),
                                percent
                            ));
                        });
                    }
                }
            }

            // KIỂM TRA LỖI / TOÀN VẸN DỮ LIỆU SHA-256
            SwingUtilities.invokeLater(() -> {
                lblDownloadStatus.setText("Đang kiểm tra tính toàn vẹn SHA-256...");
                lblDownloadStatus.setForeground(ACCENT_CYAN);
            });

            String downloadedHash = FileHashUtil.calculateSHA256(destinationFile);
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            if (duration == 0) duration = 1;

            if (downloadedHash.equalsIgnoreCase(expectedHash)) {
                log("[DOWNLOAD THÀNH CÔNG] File [" + fileName + "] đã tải xong trong " + duration + "s.");
                log("[KIỂM TRA LỖI] SHA-256 HOÀN TOÀN TRÙNG KHỚP! Dữ liệu nguyên vẹn 100%.");

                SwingUtilities.invokeLater(() -> {
                    prgDownload.setValue(100);
                    lblDownloadStatus.setText("Tải hoàn tất! Đã kiểm tra SHA-256: Dữ liệu nguyên vẹn 100%.");
                    lblDownloadStatus.setForeground(ACCENT_GREEN);
                    lblDownloadSpeed.setText("Hoàn thành");
                    JOptionPane.showMessageDialog(this,
                        "Tải thành công file: " + fileName + "\n" +
                        "Dung lượng: " + FileHashUtil.formatFileSize(destinationFile.length()) + "\n" +
                        "Mã băm SHA-256: " + downloadedHash + "\n" +
                        "Trạng thái: Dữ liệu toàn vẹn 100%!",
                        "Tải File Thành Công",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                });
            } else {
                log("[CẢNH BÁO HỎNG DỮ LIỆU] SHA-256 không khớp! File có thể bị lỗi.");
                SwingUtilities.invokeLater(() -> {
                    lblDownloadStatus.setText("CẢNH BÁO: File bị hỏng dữ liệu (Mã SHA-256 không khớp)!");
                    lblDownloadStatus.setForeground(ACCENT_RED);
                });
            }

        } catch (Exception ex) {
            log("[DOWNLOAD LỖI] " + ex.getMessage());
            SwingUtilities.invokeLater(() -> {
                lblDownloadStatus.setText("Lỗi tải file: " + ex.getMessage());
                lblDownloadStatus.setForeground(ACCENT_RED);
            });
        }
    }

    private void openDownloadFolder() {
        try {
            Desktop.getDesktop().open(downloadFolder);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể mở thư mục: " + ex.getMessage());
        }
    }

    // ================= CHIA SẺ FILE =================

    private void chooseAndShareFile() {
        if (!isConnectedToServer) {
            JOptionPane.showMessageDialog(this, "Bạn cần kết nối tới Server trước khi chia sẻ file!", "Chưa Kết Nối", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn tài liệu để chia sẻ (PDF, ZIP, Video,...)");
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            if (selectedFile.length() == 0) {
                JOptionPane.showMessageDialog(this, "File rỗng (0 bytes), không thể chia sẻ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            new Thread(() -> {
                log("[HỆ THỐNG] Đang tính mã băm SHA-256 cho file: " + selectedFile.getName() + "...");
                String hash = FileHashUtil.calculateSHA256(selectedFile);
                if (hash.isEmpty()) {
                    log("[LỖI] Không thể tính toán SHA-256.");
                    return;
                }

                LocalSharedFile localFile = new LocalSharedFile(selectedFile.getName(), selectedFile.length(), hash, selectedFile);
                mySharedFiles.put(hash, localFile);

                serverOut.println("SHARE|" + selectedFile.getName() + "|" + selectedFile.length() + "|" + hash);

                SwingUtilities.invokeLater(() -> {
                    myFilesTableModel.addRow(new Object[]{
                        selectedFile.getName(),
                        FileHashUtil.formatFileSize(selectedFile.length()),
                        hash,
                        selectedFile.getAbsolutePath()
                    });
                });

                log("[ĐÃ CHIA SẺ] Đã đăng tải file [" + selectedFile.getName() + "] lên Directory Server.");
            }).start();
        }
    }

    private void unshareSelectedFile() {
        int selectedRow = tblMyFiles.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một file trong kho của bạn để hủy chia sẻ!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String fileName = (String) myFilesTableModel.getValueAt(selectedRow, 0);
        String fileHash = (String) myFilesTableModel.getValueAt(selectedRow, 2);

        if (serverOut != null) {
            serverOut.println("UNSHARE|" + fileHash);
        }
        mySharedFiles.remove(fileHash);
        myFilesTableModel.removeRow(selectedRow);

        log("[HỦY CHIA SẺ] Đã gỡ file [" + fileName + "] khỏi hệ thống.");
    }

    // ================= KẾT NỐI SERVER =================

    private void toggleConnection() {
        if (isConnectedToServer) {
            disconnectFromServer();
        } else {
            connectToServer();
        }
    }

    private void connectToServer() {
        String peerId = txtPeerId.getText().trim();
        String host = txtServerHost.getText().trim();
        int sPort;
        int mPort;

        try {
            sPort = Integer.parseInt(txtServerPort.getText().trim());
            mPort = Integer.parseInt(txtMiniPort.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Cổng phải là số nguyên hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (peerId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập Tên Máy (Peer ID)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            serverSocket = new Socket(host, sPort);
            serverOut = new PrintWriter(new OutputStreamWriter(serverSocket.getOutputStream(), "UTF-8"), true);
            serverIn = new BufferedReader(new InputStreamReader(serverSocket.getInputStream(), "UTF-8"));

            serverOut.println("CONNECT|" + peerId + "|" + mPort);

            String response = serverIn.readLine();
            if (response != null && response.startsWith("CONNECT_OK")) {
                isConnectedToServer = true;
                startMiniServer(mPort);

                lblStatus.setText("● Đã kết nối Server");
                lblStatus.setForeground(ACCENT_GREEN);
                btnConnectServer.setText("Ngắt Kết Nối");
                btnConnectServer.setBackground(ACCENT_RED);
                btnConnectServer.repaint();

                txtPeerId.setEditable(false);
                txtMiniPort.setEditable(false);
                txtServerHost.setEditable(false);
                txtServerPort.setEditable(false);

                log("[KẾT NỐI] Đã kết nối thành công tới Directory Server (" + host + ":" + sPort + ")");

                new Thread(this::listenServerMessages, "ServerListenerThread").start();
                requestListAll();
            } else {
                String reason = (response != null && response.startsWith("ERROR|")) ? response.substring(6) : response;
                JOptionPane.showMessageDialog(this, "Server từ chối: " + reason, "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
                serverSocket.close();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối tới Server: " + ex.getMessage(), "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void disconnectFromServer() {
        if (!isConnectedToServer) return;
        isConnectedToServer = false;
        try {
            if (serverOut != null) {
                serverOut.println("DISCONNECT");
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        lblStatus.setText("○ Đã ngắt kết nối");
        lblStatus.setForeground(ACCENT_RED);
        btnConnectServer.setText("Kết Nối Server");
        btnConnectServer.setBackground(ACCENT_GREEN);
        btnConnectServer.repaint();

        txtPeerId.setEditable(true);
        txtMiniPort.setEditable(true);
        txtServerHost.setEditable(true);
        txtServerPort.setEditable(true);

        searchTableModel.setRowCount(0);
        log("[NGẮT KẾT NỐI] Đã rời khỏi hệ thống mạng.");
    }

    private void listenServerMessages() {
        try {
            String line;
            while (isConnectedToServer && (line = serverIn.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("KICKED|")) {
                    String reason = line.substring(7);
                    log("[BỊ ADMIN KICK] " + reason);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Bạn đã bị Admin ngắt kết nối khỏi hệ thống!\nLý do: " + reason,
                                "Thông Báo Kick", JOptionPane.WARNING_MESSAGE);
                        disconnectFromServer();
                    });
                    break;
                } else if (line.startsWith("FILE_REMOVED_BY_ADMIN|")) {
                    String fileHash = line.substring(22);
                    mySharedFiles.remove(fileHash);
                    SwingUtilities.invokeLater(() -> {
                        for (int i = 0; i < myFilesTableModel.getRowCount(); i++) {
                            if (myFilesTableModel.getValueAt(i, 2).equals(fileHash)) {
                                myFilesTableModel.removeRow(i);
                                break;
                            }
                        }
                    });
                    log("[THÔNG BÁO] Admin đã xóa một file của bạn khỏi danh bạ.");
                } else if (line.startsWith("SEARCH_START|")) {
                    SwingUtilities.invokeLater(() -> searchTableModel.setRowCount(0));
                } else if (line.startsWith("ITEM|")) {
                    String[] parts = line.split("\\|", -1);
                    if (parts.length >= 7) {
                        String fName = parts[1];
                        long fSize = Long.parseLong(parts[2]);
                        String fHash = parts[3];
                        String owner = parts[4];
                        String ipPort = parts[5] + ":" + parts[6];

                        SwingUtilities.invokeLater(() -> {
                            searchTableModel.addRow(new Object[]{
                                fName,
                                FileHashUtil.formatFileSize(fSize),
                                fHash,
                                owner,
                                ipPort
                            });
                        });
                    }
                }
            }
        } catch (IOException e) {
            if (isConnectedToServer) {
                SwingUtilities.invokeLater(this::disconnectFromServer);
            }
        }
    }

    private void performSearch() {
        if (!isConnectedToServer) {
            JOptionPane.showMessageDialog(this, "Bạn cần kết nối Server trước!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String keyword = txtSearch.getText().trim();
        serverOut.println("SEARCH|" + keyword);
        log("[TÌM KIẾM] Đang tìm kiếm tài liệu với từ khóa: \"" + keyword + "\"...");
    }

    private void requestListAll() {
        if (!isConnectedToServer) return;
        serverOut.println("LIST_ALL");
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            txtLog.append("[" + timestamp + "] " + msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    // ================= UTILITIES & STYLES =================

    private JLabel createLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_BODY);
        lbl.setForeground(TEXT_MAIN);
        return lbl;
    }

    private JTextField createTextField(String text, int cols) {
        JTextField tf = new JTextField(text, cols);
        tf.setFont(FONT_BODY);
        tf.setBackground(CARD_BG);
        tf.setForeground(Color.WHITE);
        tf.setCaretColor(Color.WHITE);
        tf.setDisabledTextColor(Color.WHITE);
        tf.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_COLOR, 1, true),
            new EmptyBorder(3, 6, 3, 6)
        ));
        return tf;
    }

    public static JButton createCustomButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color currentBg = bg;
                if (getModel().isPressed()) {
                    currentBg = bg.darker();
                } else if (getModel().isRollover()) {
                    currentBg = bg.brighter();
                }

                g2.setColor(currentBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

                g2.setColor(new Color(255, 255, 255, 60));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

                g2.dispose();
                super.paintComponent(g);
            }
        };

        btn.setUI(new BasicButtonUI());
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setFont(FONT_HEADER);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(7, 14, 7, 14));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void setupTableStyle(JTable table) {
        table.setBackground(TABLE_ROW_MAIN);
        table.setForeground(TEXT_MAIN);
        table.setFont(FONT_BODY);
        table.setRowHeight(30);
        table.setSelectionBackground(TABLE_SELECTED);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(BORDER_COLOR);
        table.setShowGrid(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTableHeader header = table.getTableHeader();
        header.setBackground(TABLE_HEADER_BG);
        header.setForeground(Color.WHITE);
        header.setFont(FONT_HEADER);
        header.setBorder(new LineBorder(BORDER_COLOR, 1));
        header.setReorderingAllowed(false);

        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean focus, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, val, sel, focus, row, col);
                lbl.setBackground(new Color(15, 23, 42));
                lbl.setForeground(ACCENT_CYAN);
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
                lbl.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(BORDER_COLOR, 1),
                    new EmptyBorder(6, 8, 6, 8)
                ));
                return lbl;
            }
        });

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean focus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, focus, row, col);
                if (!sel) {
                    c.setBackground(row % 2 == 0 ? TABLE_ROW_MAIN : TABLE_ROW_ALT);
                    c.setForeground(TEXT_MAIN);
                } else {
                    c.setBackground(TABLE_SELECTED);
                    c.setForeground(Color.WHITE);
                }
                setBorder(new EmptyBorder(0, 8, 0, 8));
                return c;
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            P2PFileNode node = new P2PFileNode();
            node.setVisible(true);
        });
    }
}
