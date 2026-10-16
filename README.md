# HỆ THỐNG P2P FILE SHARING - ĐỒ ÁN LẬP TRÌNH MẠNG

Hệ thống chia sẻ tập tin ngang hàng (P2P) kết hợp Máy chủ chỉ mục trung tâm (Directory Server), được xây dựng hoàn toàn bằng **Java Socket & Java Swing GUI** hiện đại.

---

## 📂 3 GIAO DIỆN CHUYÊN BIỆT TRONG DỰ ÁN:

### 1. Giao diện Máy Chủ: `ServerGUI.java` (Chạy bằng `Chay_Server.bat`)
- Điều khiển dịch vụ mạng: Khởi động/Dừng Server Socket tại cổng 5000.
- Giám sát địa chỉ IP (Radmin VPN, LAN), số lượng kết nối Socket thời gian thực.
- Khung Network Logs ghi nhận lưu lượng gói tin mạng chi tiết.
- Có nút bấm **"👑 Mở Bảng Quản Trị Admin"** để gọi nhanh giao diện Admin.

### 2. Giao diện Quản Trị Viên: `AdminGUI.java` (Chạy bằng `Chay_Admin.bat`)
- Thống kê toàn hệ thống: Số Peer online, Tổng tài liệu chia sẻ, Tổng dung lượng mạng.
- **Bảng 1 (Quản lý Peer):** Theo dõi danh sách máy trạm, nút **`⛔ Kick (Ngắt kết nối) Peer`** để xử lý người dùng vi phạm.
- **Bảng 2 (Quản lý Tài liệu):** Duyệt danh bạ file hệ thống, nút **`🗑 Xóa Tài Liệu Khỏi Danh Bạ`** để gỡ file vi phạm bản quyền.
- **Khung 3 (Audit Logs):** Ghi chép nhật ký hành động nghiệp vụ của Admin.

### 3. Giao diện Máy Trạm: `P2PFileNode.java` (Chạy bằng `Chay_Peer_Client.bat`)
- **Vai trò Mini-Server (Người đăng):** ServerSocket chạy ngầm mở cổng riêng, đọc file từ ổ cứng bắn qua cho máy khác.
- **Vai trò Downloader (Người lấy):** Kéo luồng byte từ máy bạn về, thanh tiến trình `0% -> 100%`.
- **Kiểm tra lỗi (Data Integrity):** Băm SHA-256 đối soát tính toàn vẹn 100% của file tải về.
