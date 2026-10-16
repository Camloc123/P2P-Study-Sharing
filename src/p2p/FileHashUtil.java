package p2p;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Tiện ích băm file (SHA-256 / MD5) để kiểm tra tính toàn vẹn dữ liệu
 * Đảm bảo file lớn (PDF, ZIP, Video) truyền qua mạng không bị hỏng hóc hoặc thiếu byte.
 */
public class FileHashUtil {

    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB buffer

    /**
     * Tính toán mã băm SHA-256 của một file trên ổ đĩa.
     * Đọc theo từng buffer 64KB để không làm tràn RAM khi xử lý file dung lượng lớn.
     */
    public static String calculateSHA256(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return "";
        }
        try (InputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        } catch (Exception e) {
            System.err.println("Lỗi khi tính SHA-256: " + e.getMessage());
            return "";
        }
    }

    /**
     * Định dạng dung lượng byte sang chuỗi thân thiện (B, KB, MB, GB).
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
