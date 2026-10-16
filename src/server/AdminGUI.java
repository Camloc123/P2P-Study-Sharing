package server;

import p2p.FileHashUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * GIAO DIỆN ADMIN (AdminGUI.java)
 * Chuyên biệt cho Quản trị viên:
 * - Theo dõi, quản lý và Kick Peer vi phạm.
 * - Quản lý danh bạ tài liệu và Xóa tài liệu khỏi hệ thống.
 * - Bảng điều khiển quản trị (Admin Dashboard) với thống kê số liệu chi tiết.
 */
public class AdminGUI extends JFrame implements DirectoryServer.ServerObserver {

    // Palette màu sắc hiện đại cho Admin Console (Chủ đạo: Tím & Đỏ quyền lực)
    private static final Color BG_DARK = new Color(15, 23, 42);          // Slate 900
    private static final Color PANEL_BG = new Color(30, 41, 59);         // Slate 800
    private static final Color CARD_BG = new Color(51, 65, 85);          // Slate 700
    private static final Color BORDER_COLOR = new Color(100, 116, 139);  // Slate 500
    private static final Color ACCENT_PURPLE = new Color(168, 85, 247);  // Purple 400
    private static final Color ACCENT_CYAN = new Color(56, 189, 248);    // Sky 400
    private static final Color ACCENT_GREEN = new Color(34, 197, 94);    // Green 500
    private static final Color ACCENT_RED = new Color(239, 68, 68);      // Red 500
    private static final Color TEXT_MAIN = new Color(248, 250, 252);     // Pure White
    private static final Color TEXT_MUTED = new Color(203, 213, 225);    // Slate 300
    private static final Color TABLE_HEADER_BG = new Color(15, 23, 42);
    private static final Color TABLE_ROW_ALT = new Color(30, 41, 59);
    private static final Color TABLE_ROW_MAIN = new Color(39, 54, 76);
    private static final Color TABLE_SELECTED = new Color(126, 34, 206); // Purple 700

    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 17);
    private static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_MONO = new Font("Consolas", Font.BOLD, 12);
    private static final Font FONT_STAT_NUM = new Font("Segoe UI", Font.BOLD, 22);

    private DirectoryServer server;

    // Stat Cards
    private JLabel lblPeerCount;
    private JLabel lblFileCount;
    private JLabel lblTotalSize;

    // Tables
    private DefaultTableModel peerTableModel;
    private JTable tblPeers;
    private DefaultTableModel fileTableModel;
    private JTable tblFiles;

    // Admin Audit Log
    private JTextArea txtAdminLog;

    public AdminGUI(DirectoryServer server) {
        this.server = server;
        setTitle("BẢNG ĐIỀU KHIỂN QUẢN TRỊ ADMIN (ADMIN DASHBOARD)");
        setSize(1200, 750);
        setMinimumSize(new Dimension(980, 650));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        initComponents();

        if (server != null) {
            server.addObserver(this);
            loadInitialData();
        }
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        // 1. Top Bar: Header & Stat Cards
        add(createTopBar(), BorderLayout.NORTH);

        // 2. Center: Hai bảng quản lý (Peer & Files)
        JSplitPane splitTables = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitTables.setTopComponent(createPeerManagementPanel());
        splitTables.setBottomComponent(createFileManagementPanel());
        splitTables.setResizeWeight(0.45);
        splitTables.setDividerSize(6);
        splitTables.setBorder(null);
        splitTables.setBackground(BG_DARK);

        // 3. Bottom: Nhật ký hành động Admin (Audit Log)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplit.setTopComponent(splitTables);
        mainSplit.setBottomComponent(createAdminLogPanel());
        mainSplit.setResizeWeight(0.72);
        mainSplit.setDividerSize(6);
        mainSplit.setBorder(null);
        mainSplit.setBackground(BG_DARK);

        add(mainSplit, BorderLayout.CENTER);
    }

    private JPanel createTopBar() {
        JPanel panel = new JPanel(new BorderLayout(15, 10));
        panel.setBackground(PANEL_BG);
        panel.setBorder(new EmptyBorder(12, 18, 12, 18));

        // Tiêu đề Admin
        JPanel left = new JPanel(new GridLayout(2, 1, 0, 4));
        left.setOpaque(false);

        JLabel lblTitle = new JLabel("TRUNG TÂM QUẢN TRỊ ADMIN (ADMIN MANAGEMENT)");
        lblTitle.setFont(FONT_TITLE);
        lblTitle.setForeground(ACCENT_PURPLE);

        JLabel lblSub = new JLabel("Quyền Quản Trị: Giám sát Peer, Xóa tài liệu vi phạm & Kick người dùng");
        lblSub.setFont(FONT_BODY);
        lblSub.setForeground(TEXT_MUTED);

        left.add(lblTitle);
        left.add(lblSub);
        panel.add(left, BorderLayout.WEST);

        // Thẻ thống kê
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        JPanel cardPeers = createStatCard("PEER TRỰC TUYẾN", "0", ACCENT_CYAN);
        lblPeerCount = (JLabel) ((JPanel) cardPeers.getComponent(1)).getComponent(0);

        JPanel cardFiles = createStatCard("TỔNG TÀI LIỆU", "0", ACCENT_GREEN);
        lblFileCount = (JLabel) ((JPanel) cardFiles.getComponent(1)).getComponent(0);

        JPanel cardSize = createStatCard("DUNG LƯỢNG CHỈ MỤC", "0 B", ACCENT_PURPLE);
        lblTotalSize = (JLabel) ((JPanel) cardSize.getComponent(1)).getComponent(0);

        right.add(cardPeers);
        right.add(cardFiles);
        right.add(cardSize);

        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private JPanel createStatCard(String title, String initialValue, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(4, 2));
        card.setBackground(CARD_BG);
        card.setPreferredSize(new Dimension(145, 54));
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

    private JPanel createPeerManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(6, 12, 4, 12),
            BorderFactory.createTitledBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                " 1. QUẢN LÝ DANH SÁCH PEER (MÁY TRẠM ĐANG KẾT NỐI) ",
                0, 0, FONT_HEADER, ACCENT_CYAN
            )
        ));

        String[] columns = {"Peer ID (Tên)", "Địa chỉ IP", "Cổng P2P Node", "Thời gian kết nối", "Trạng thái"};
        peerTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        tblPeers = new JTable(peerTableModel);
        setupTableStyle(tblPeers);

        JScrollPane scrollPane = new JScrollPane(tblPeers);
        scrollPane.getViewport().setBackground(PANEL_BG);
        scrollPane.setBorder(new LineBorder(BORDER_COLOR, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Thanh thao tác của Admin
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        actionBar.setOpaque(false);

        JButton btnKick = createCustomButton("⛔ Kick (Ngắt kết nối) Peer", ACCENT_RED, Color.WHITE);
        btnKick.setToolTipText("Ngắt kết nối máy trạm được chọn và gỡ toàn bộ tài liệu của máy đó");
        btnKick.addActionListener(e -> kickSelectedPeer());

        actionBar.add(btnKick);
        panel.add(actionBar, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createFileManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(4, 12, 4, 12),
            BorderFactory.createTitledBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                " 2. QUẢN LÝ DANH BẠ TÀI LIỆU (FILE CHIA SẺ TRÊN HỆ THỐNG) ",
                0, 0, FONT_HEADER, ACCENT_GREEN
            )
        ));

        String[] columns = {"Tên tài liệu", "Dung lượng", "Mã băm SHA-256", "Người đăng (Peer ID)", "IP:Port Máy Trạm"};
        fileTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        tblFiles = new JTable(fileTableModel);
        setupTableStyle(tblFiles);

        tblFiles.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                setFont(FONT_MONO);
                setForeground(isSelected ? Color.WHITE : ACCENT_CYAN);
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(tblFiles);
        scrollPane.getViewport().setBackground(PANEL_BG);
        scrollPane.setBorder(new LineBorder(BORDER_COLOR, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Thanh thao tác của Admin
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        actionBar.setOpaque(false);

        JButton btnDeleteFile = createCustomButton("🗑 Xóa Tài Liệu Khỏi Danh Bạ", ACCENT_RED, Color.WHITE);
        btnDeleteFile.setToolTipText("Admin gỡ vĩnh viễn tài liệu được chọn ra khỏi hệ thống chỉ mục");
        btnDeleteFile.addActionListener(e -> deleteSelectedFile());

        actionBar.add(btnDeleteFile);
        panel.add(actionBar, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createAdminLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(4, 12, 8, 12),
            BorderFactory.createTitledBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                " 3. NHẬT KÝ THAO TÁC ADMIN (AUDIT & SECURITY LOGS) ",
                0, 0, FONT_HEADER, TEXT_MUTED
            )
        ));

        txtAdminLog = new JTextArea();
        txtAdminLog.setBackground(new Color(10, 15, 30));
        txtAdminLog.setForeground(new Color(241, 245, 249));
        txtAdminLog.setFont(FONT_MONO);
        txtAdminLog.setEditable(false);
        txtAdminLog.setLineWrap(true);
        txtAdminLog.setWrapStyleWord(true);
        txtAdminLog.setMargin(new Insets(8, 8, 8, 8));

        JScrollPane scrollPane = new JScrollPane(txtAdminLog);
        scrollPane.setBorder(new LineBorder(BORDER_COLOR, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        bottomBar.setOpaque(false);

        JButton btnClearLog = createCustomButton("Xóa Log", CARD_BG, TEXT_MAIN);
        btnClearLog.addActionListener(e -> txtAdminLog.setText(""));
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
                lbl.setForeground(ACCENT_PURPLE);
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

    private void loadInitialData() {
        if (server == null) return;
        peerTableModel.setRowCount(0);
        for (DirectoryServer.PeerInfo peer : server.getActivePeers().values()) {
            peerTableModel.addRow(new Object[]{
                peer.getPeerId(),
                peer.getIpAddress(),
                peer.getP2pPort(),
                peer.getConnectedAt(),
                "ONLINE"
            });
        }
        lblPeerCount.setText(String.valueOf(peerTableModel.getRowCount()));

        fileTableModel.setRowCount(0);
        long totalBytes = 0;
        for (DirectoryServer.SharedFileInfo file : server.getFileDirectory().values()) {
            fileTableModel.addRow(new Object[]{
                file.getFileName(),
                FileHashUtil.formatFileSize(file.getFileSize()),
                file.getFileHash(),
                file.getOwnerPeerId(),
                file.getOwnerIp() + ":" + file.getOwnerP2pPort()
            });
            totalBytes += file.getFileSize();
        }
        lblFileCount.setText(String.valueOf(fileTableModel.getRowCount()));
        lblTotalSize.setText(FileHashUtil.formatFileSize(totalBytes));

        appendAdminLog("Đã đồng bộ dữ liệu quản trị với Server.");
    }

    private void kickSelectedPeer() {
        int selectedRow = tblPeers.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một Peer trong danh sách để Kick!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String peerId = (String) peerTableModel.getValueAt(selectedRow, 0);
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Bạn có chắc muốn Kick Peer [" + peerId + "] và gỡ toàn bộ tài liệu của họ?",
            "Xác nhận Kick Peer",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            if (server != null) {
                boolean success = server.kickPeer(peerId, "Bị Quản Trị Viên ngắt kết nối (Admin Kick)");
                if (success) {
                    appendAdminLog("[QUYỀN ADMIN] Đã Kick thành công Peer: " + peerId);
                    JOptionPane.showMessageDialog(this, "Đã ngắt kết nối Peer [" + peerId + "] thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }
    }

    private void deleteSelectedFile() {
        int selectedRow = tblFiles.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tài liệu trong bảng để xóa!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String fileName = (String) fileTableModel.getValueAt(selectedRow, 0);
        String fileHash = (String) fileTableModel.getValueAt(selectedRow, 2);
        String ownerPeerId = (String) fileTableModel.getValueAt(selectedRow, 3);

        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Bạn có chắc muốn xóa tài liệu [" + fileName + "] của Peer [" + ownerPeerId + "] khỏi danh bạ?",
            "Xác nhận Xóa Tài Liệu",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            if (server != null) {
                boolean success = server.deleteFileFromDirectory(fileHash, ownerPeerId);
                if (success) {
                    appendAdminLog("[QUYỀN ADMIN] Đã xóa tài liệu [" + fileName + "] của " + ownerPeerId);
                    JOptionPane.showMessageDialog(this, "Đã xóa tài liệu khỏi danh bạ hệ thống!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }
    }

    private void appendAdminLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            txtAdminLog.append("[" + timestamp + "] " + msg + "\n");
            txtAdminLog.setCaretPosition(txtAdminLog.getDocument().getLength());
        });
    }

    // ================= CALLBACKS =================

    @Override
    public void onLog(String message) {
        // Có thể lọc log hiển thị cho admin
    }

    @Override
    public void onPeerConnected(DirectoryServer.PeerInfo peer) {
        SwingUtilities.invokeLater(() -> {
            peerTableModel.addRow(new Object[]{
                peer.getPeerId(),
                peer.getIpAddress(),
                peer.getP2pPort(),
                peer.getConnectedAt(),
                "ONLINE"
            });
            lblPeerCount.setText(String.valueOf(peerTableModel.getRowCount()));
            appendAdminLog("[THÀNH VIÊN MỚI] Peer [" + peer.getPeerId() + "] kết nối từ " + peer.getIpAddress());
        });
    }

    @Override
    public void onPeerDisconnected(String peerId) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < peerTableModel.getRowCount(); i++) {
                if (peerTableModel.getValueAt(i, 0).equals(peerId)) {
                    peerTableModel.removeRow(i);
                    break;
                }
            }
            lblPeerCount.setText(String.valueOf(peerTableModel.getRowCount()));

            for (int i = fileTableModel.getRowCount() - 1; i >= 0; i--) {
                if (fileTableModel.getValueAt(i, 3).equals(peerId)) {
                    fileTableModel.removeRow(i);
                }
            }
            lblFileCount.setText(String.valueOf(fileTableModel.getRowCount()));
            appendAdminLog("[NGẮT KẾT NỐI] Peer [" + peerId + "] đã rời mạng.");
        });
    }

    @Override
    public void onFileShared(DirectoryServer.SharedFileInfo file) {
        SwingUtilities.invokeLater(() -> {
            fileTableModel.addRow(new Object[]{
                file.getFileName(),
                FileHashUtil.formatFileSize(file.getFileSize()),
                file.getFileHash(),
                file.getOwnerPeerId(),
                file.getOwnerIp() + ":" + file.getOwnerP2pPort()
            });
            lblFileCount.setText(String.valueOf(fileTableModel.getRowCount()));
            appendAdminLog("[TÀI LIỆU MỚI] Peer [" + file.getOwnerPeerId() + "] đã đăng: " + file.getFileName());
        });
    }

    @Override
    public void onFileUnshared(String fileHash, String ownerPeerId) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < fileTableModel.getRowCount(); i++) {
                String hash = (String) fileTableModel.getValueAt(i, 2);
                String owner = (String) fileTableModel.getValueAt(i, 3);
                if (hash.equals(fileHash) && owner.equals(ownerPeerId)) {
                    fileTableModel.removeRow(i);
                    break;
                }
            }
            lblFileCount.setText(String.valueOf(fileTableModel.getRowCount()));
        });
    }

    @Override
    public void onServerStatusChanged(boolean running, int port) {
        SwingUtilities.invokeLater(() -> {
            if (!running) {
                peerTableModel.setRowCount(0);
                fileTableModel.setRowCount(0);
                lblPeerCount.setText("0");
                lblFileCount.setText("0");
                lblTotalSize.setText("0 B");
                appendAdminLog("[CẢNH BÁO] Server đã dừng hoạt động!");
            } else {
                appendAdminLog("[HỆ THỐNG] Server đã khởi động tại cổng: " + port);
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Mở AdminGUI độc lập (sử dụng instance server nếu có)
            AdminGUI gui = new AdminGUI(null);
            gui.setVisible(true);
        });
    }
}
