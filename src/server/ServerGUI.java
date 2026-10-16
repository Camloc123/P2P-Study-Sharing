package server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * GIAO DIỆN MÁY CHỦ (ServerGUI.java)
 * Chuyên trách điều khiển dịch vụ mạng (Server Service & Network Monitor):
 * - Bật/Dừng Server Socket cổng 5000.
 * - Giám sát địa chỉ IP (Radmin VPN, LAN), trạng thái kết nối socket.
 * - Hiển thị chi tiết lưu lượng gói tin (Network Traffic Logs).
 * - Nút tích hợp mở nhanh Bảng Quản Trị Admin (AdminGUI).
 */
public class ServerGUI extends JFrame implements DirectoryServer.ServerObserver {

    // Theme màu Cyber Terminal hiện đại (Chủ đạo: Xanh Cyan & Navy)
    private static final Color BG_DARK = new Color(15, 23, 42);          // Slate 900
    private static final Color PANEL_BG = new Color(30, 41, 59);         // Slate 800
    private static final Color CARD_BG = new Color(51, 65, 85);          // Slate 700
    private static final Color BORDER_COLOR = new Color(100, 116, 139);  // Slate 500
    private static final Color ACCENT_CYAN = new Color(56, 189, 248);    // Sky 400
    private static final Color ACCENT_GREEN = new Color(34, 197, 94);    // Green 500
    private static final Color ACCENT_RED = new Color(239, 68, 68);      // Red 500
    private static final Color ACCENT_PURPLE = new Color(168, 85, 247);  // Purple 400
    private static final Color TEXT_MAIN = new Color(248, 250, 252);     // Pure White
    private static final Color TEXT_MUTED = new Color(203, 213, 225);    // Slate 300
    private static final Color TABLE_HEADER_BG = new Color(15, 23, 42);
    private static final Color TABLE_ROW_ALT = new Color(30, 41, 59);
    private static final Color TABLE_ROW_MAIN = new Color(39, 54, 76);
    private static final Color TABLE_SELECTED = new Color(14, 116, 144);

    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 17);
    private static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_MONO = new Font("Consolas", Font.BOLD, 12);
    private static final Font FONT_STAT_NUM = new Font("Segoe UI", Font.BOLD, 22);

    private DirectoryServer server;
    private int serverPort = DirectoryServer.DEFAULT_PORT;
    private AdminGUI openAdminGUI = null;

    // Components
    private JLabel lblStatusBadge;
    private JLabel lblConnCount;
    private JLabel lblReqCount;
    private JButton btnToggleServer;
    private JTextField txtPort;
    private long totalRequestsServed = 0;

    // Table giám sát socket
    private DefaultTableModel connTableModel;
    private JTable tblConnections;

    // Log mạng
    private JTextArea txtNetworkLog;

    public ServerGUI() {
        setTitle("MÁY CHỦ TRUNG TÂM - GIÁM SÁT MẠNG P2P (SERVER CONSOLE)");
        setSize(1180, 720);
        setMinimumSize(new Dimension(960, 620));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        initComponents();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (server != null && server.isRunning()) {
                    server.stopServer();
                }
            }
        });

        // Tự động khởi động Server
        startServerAction();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        // 1. Top Panel: Header, Cổng & Thống kê
        add(createTopPanel(), BorderLayout.NORTH);

        // 2. Center: SplitPane chứa bảng Giám Sát Socket & Khung Network Log
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplit.setTopComponent(createConnectionMonitorPanel());
        mainSplit.setBottomComponent(createNetworkLogPanel());
        mainSplit.setResizeWeight(0.42);
        mainSplit.setDividerSize(6);
        mainSplit.setBorder(null);
        mainSplit.setBackground(BG_DARK);

        add(mainSplit, BorderLayout.CENTER);
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 10));
        panel.setBackground(PANEL_BG);
        panel.setBorder(new EmptyBorder(12, 18, 12, 18));

        // Bên trái: Tiêu đề + Badges mạng
        JPanel left = new JPanel(new GridLayout(2, 1, 0, 6));
        left.setOpaque(false);

        JLabel lblTitle = new JLabel("MÁY CHỦ TRUNG TÂM (CENTRAL DIRECTORY SERVER)");
        lblTitle.setFont(FONT_TITLE);
        lblTitle.setForeground(ACCENT_CYAN);

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badgeRow.setOpaque(false);

        lblStatusBadge = new JLabel("● ĐANG CHẠY");
        lblStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatusBadge.setForeground(ACCENT_GREEN);
        JPanel pnlStatusBadge = createBadgePanel(lblStatusBadge, new Color(5, 46, 22), new Color(22, 101, 52));

        String radminIp = getRadminIp();
        JLabel lblRadmin = new JLabel("Radmin VPN: " + (radminIp != null ? radminIp : "Chưa bật"));
        lblRadmin.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblRadmin.setForeground(new Color(165, 180, 252));
        JPanel pnlRadminBadge = createBadgePanel(lblRadmin, new Color(30, 27, 75), new Color(67, 56, 202));

        JLabel lblPortBadge = new JLabel("Cổng: " + serverPort);
        lblPortBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblPortBadge.setForeground(new Color(226, 232, 240));
        JPanel pnlPortBadge = createBadgePanel(lblPortBadge, new Color(15, 23, 42), BORDER_COLOR);

        badgeRow.add(pnlStatusBadge);
        badgeRow.add(pnlRadminBadge);
        badgeRow.add(pnlPortBadge);

        left.add(lblTitle);
        left.add(badgeRow);
        panel.add(left, BorderLayout.WEST);

        // Bên phải: Stat Cards + Nút Mở Admin Panel + Điều khiển
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);

        JPanel cardConn = createStatCard("KẾT NỐI SOCKET", "0", ACCENT_CYAN);
        lblConnCount = (JLabel) ((JPanel) cardConn.getComponent(1)).getComponent(0);

        JPanel cardReq = createStatCard("REQUEST ĐÃ XỬ LÝ", "0", ACCENT_GREEN);
        lblReqCount = (JLabel) ((JPanel) cardReq.getComponent(1)).getComponent(0);

        // Nút mở Bảng Admin riêng biệt
        JButton btnOpenAdmin = createCustomButton("👑 Mở Bảng Quản Trị Admin", ACCENT_PURPLE, Color.WHITE);
        btnOpenAdmin.setToolTipText("Mở giao diện quản trị viên chuyên nghiệp để quản lý Peer và Xóa tài liệu");
        btnOpenAdmin.addActionListener(e -> openAdminDashboard());

        // Điều khiển Bật/Dừng Server
        JPanel portBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        portBox.setBackground(CARD_BG);
        portBox.setBorder(new LineBorder(BORDER_COLOR, 1, true));

        JLabel lblPort = new JLabel("Cổng:");
        lblPort.setForeground(TEXT_MAIN);
        lblPort.setFont(FONT_BODY);

        txtPort = new JTextField(String.valueOf(serverPort), 4);
        txtPort.setFont(FONT_HEADER);
        txtPort.setBackground(PANEL_BG);
        txtPort.setForeground(TEXT_MAIN);
        txtPort.setCaretColor(TEXT_MAIN);
        txtPort.setHorizontalAlignment(JTextField.CENTER);
        txtPort.setBorder(new EmptyBorder(4, 6, 4, 6));

        btnToggleServer = createCustomButton("Dừng Server", ACCENT_RED, Color.WHITE);
        btnToggleServer.addActionListener(e -> toggleServerAction());

        portBox.add(lblPort);
        portBox.add(txtPort);
        portBox.add(btnToggleServer);

        right.add(cardConn);
        right.add(cardReq);
        right.add(btnOpenAdmin);
        right.add(portBox);

        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private JPanel createBadgePanel(JLabel label, Color bg, Color border) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 3));
        p.setBackground(bg);
        p.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(border, 1, true),
            new EmptyBorder(2, 6, 2, 6)
        ));
        p.add(label);
        return p;
    }

    private JPanel createStatCard(String title, String initialValue, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(4, 2));
        card.setBackground(CARD_BG);
        card.setPreferredSize(new Dimension(135, 54));
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(BORDER_COLOR, 1, true),
            new EmptyBorder(4, 10, 4, 10)
        ));

        JLabel lblT = new JLabel(title, SwingConstants.CENTER);
        lblT.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblT.setForeground(TEXT_MUTED);

        JPanel valBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        valBox.setOpaque(false);
        JLabel lblV = new JLabel(initialValue);
        lblV.setFont(FONT_STAT_NUM);
        lblV.setForeground(accentColor);
        valBox.add(lblV);

        card.add(lblT, BorderLayout.NORTH);
        card.add(valBox, BorderLayout.CENTER);
        return card;
    }

    /**
     * Bảng Giám Sát Kết Nối Mạng Socket (Network Socket Monitor)
     */
    private JPanel createConnectionMonitorPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(6, 12, 4, 12),
            BorderFactory.createTitledBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                " 1. GIÁM SÁT KẾT NỐI MẠNG (ACTIVE SOCKET CONNECTIONS) ",
                0, 0, FONT_HEADER, ACCENT_CYAN
            )
        ));

        String[] columns = {"Peer ID", "Địa chỉ IP Máy Trạm", "Cổng P2P Node", "Thời Điểm Kết Nối", "Trạng Thái Socket"};
        connTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        tblConnections = new JTable(connTableModel);
        setupTableStyle(tblConnections);

        JScrollPane scrollPane = new JScrollPane(tblConnections);
        scrollPane.getViewport().setBackground(PANEL_BG);
        scrollPane.setBorder(new LineBorder(BORDER_COLOR, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Khung Network Traffic Log
     */
    private JPanel createNetworkLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(4, 12, 8, 12),
            BorderFactory.createTitledBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                " 2. NHẬT KÝ LƯU LƯỢNG MẠNG & GÓI TIN (NETWORK TRAFFIC & PROTOCOL LOGS) ",
                0, 0, FONT_HEADER, TEXT_MUTED
            )
        ));

        txtNetworkLog = new JTextArea();
        txtNetworkLog.setBackground(new Color(10, 15, 30));
        txtNetworkLog.setForeground(new Color(241, 245, 249));
        txtNetworkLog.setFont(FONT_MONO);
        txtNetworkLog.setEditable(false);
        txtNetworkLog.setLineWrap(true);
        txtNetworkLog.setWrapStyleWord(true);
        txtNetworkLog.setMargin(new Insets(8, 8, 8, 8));

        JScrollPane scrollPane = new JScrollPane(txtNetworkLog);
        scrollPane.setBorder(new LineBorder(BORDER_COLOR, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        bottomBar.setOpaque(false);

        JButton btnClearLog = createCustomButton("Xóa Log Mạng", CARD_BG, TEXT_MAIN);
        btnClearLog.addActionListener(e -> txtNetworkLog.setText(""));
        bottomBar.add(btnClearLog);

        panel.add(bottomBar, BorderLayout.SOUTH);
        return panel;
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

                g2.setColor(new Color(255, 255, 255, 50));
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
        btn.setBorder(new EmptyBorder(8, 16, 8, 16));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ================= ACTIONS =================

    private void openAdminDashboard() {
        if (openAdminGUI == null || !openAdminGUI.isDisplayable()) {
            openAdminGUI = new AdminGUI(server);
            openAdminGUI.setVisible(true);
        } else {
            openAdminGUI.toFront();
            openAdminGUI.requestFocus();
        }
    }

    private void toggleServerAction() {
        if (server != null && server.isRunning()) {
            server.stopServer();
        } else {
            startServerAction();
        }
    }

    private void startServerAction() {
        try {
            int port = Integer.parseInt(txtPort.getText().trim());
            serverPort = port;
            server = new DirectoryServer(serverPort);
            server.addObserver(this);
            server.startServer();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Cổng không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể mở Server tại cổng " + serverPort + ":\n" + ex.getMessage(),
                    "Lỗi Khởi Động", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ================= CALLBACKS =================

    @Override
    public void onLog(String message) {
        totalRequestsServed++;
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            txtNetworkLog.append("[" + timestamp + "] " + message + "\n");
            txtNetworkLog.setCaretPosition(txtNetworkLog.getDocument().getLength());
            lblReqCount.setText(String.valueOf(totalRequestsServed));
        });
    }

    @Override
    public void onPeerConnected(DirectoryServer.PeerInfo peer) {
        SwingUtilities.invokeLater(() -> {
            connTableModel.addRow(new Object[]{
                peer.getPeerId(),
                peer.getIpAddress(),
                peer.getP2pPort(),
                peer.getConnectedAt(),
                "ESTABLISHED"
            });
            lblConnCount.setText(String.valueOf(connTableModel.getRowCount()));
        });
    }

    @Override
    public void onPeerDisconnected(String peerId) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < connTableModel.getRowCount(); i++) {
                if (connTableModel.getValueAt(i, 0).equals(peerId)) {
                    connTableModel.removeRow(i);
                    break;
                }
            }
            lblConnCount.setText(String.valueOf(connTableModel.getRowCount()));
        });
    }

    @Override
    public void onFileShared(DirectoryServer.SharedFileInfo file) {}

    @Override
    public void onFileUnshared(String fileHash, String ownerPeerId) {}

    @Override
    public void onServerStatusChanged(boolean running, int port) {
        SwingUtilities.invokeLater(() -> {
            if (running) {
                lblStatusBadge.setText("● ĐANG CHẠY");
                lblStatusBadge.setForeground(ACCENT_GREEN);
                btnToggleServer.setText("Dừng Server");
                btnToggleServer.setBackground(ACCENT_RED);
                btnToggleServer.repaint();
                txtPort.setEditable(false);
            } else {
                lblStatusBadge.setText("○ ĐÃ DỪNG");
                lblStatusBadge.setForeground(ACCENT_RED);
                btnToggleServer.setText("Khởi Động Server");
                btnToggleServer.setBackground(ACCENT_GREEN);
                btnToggleServer.repaint();
                txtPort.setEditable(true);
                connTableModel.setRowCount(0);
                lblConnCount.setText("0");
            }
        });
    }

    private static String getRadminIp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("26.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            gui.setVisible(true);
        });
    }
}
