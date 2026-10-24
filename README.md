# 🎟️ EventHunting Backend API — Nền Tảng Quản Lý & Đặt Vé Sự Kiện Trực Tuyến

<div align="center">

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.5-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-JWT-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-6.0+-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)

<p align="center">
  <b>Giải pháp số hóa toàn diện quy trình tổ chức và bán vé sự kiện.</b><br>
  Hỗ trợ khách hàng đặt vé và chọn ghế trực tuyến an toàn qua <b>VNPay</b>, đồng thời cung cấp công cụ cho Nhà tổ chức quản lý suất diễn, thống kê doanh thu và quét mã <b>QR check-in</b> tại quầy.
</p>

</div>

---

## 📌 Dành Cho Nhà Tuyển Dụng & Đánh Giá (Recruiter Quick Access)

| Vai trò | Email / Tài khoản | Mật khẩu | Phạm vi quyền hạn |
| :--- | :--- | :--- | :--- |
| **👑 ADMIN** | `Admin1@gmail.com` | `123456` | Toàn quyền kiểm duyệt sự kiện, quản lý người dùng, phân quyền RBAC, quản lý danh mục, voucher toàn sàn và xem dashboard doanh thu. |
| **👤 USER** | `hodacuy260102@gmail.com` | `123456` | Tìm kiếm sự kiện, đặt giữ chỗ theo thời gian thực, áp dụng mã giảm giá, thanh toán VNPay và nhận vé điện tử (PDF/QR). |

---

## 🚀 Tính Năng Cốt Lõi (Core Features)

### 1. Giải quyết bài toán Race Condition & Giữ ghế (Seat Hold)
* **Vấn đề:** Khi mở bán vé các sự kiện hot (Flash Sale / Concert), hàng nghìn người cùng tranh mua các vị trí ghế VIP hoặc số lượng vé có hạn, dẫn đến lỗi **Overbooking** (bán lố vé) và xung đột giữ chỗ đồng thời (Check-then-Act Race Condition).
* **Giải pháp:** 
  * **Redis Lua Script nguyên tử (Atomicity):** Đóng gói toàn bộ logic kiểm tra ghế trống / số lượng vé và khóa ghế vào các kịch bản **Lua Script**, đảm bảo giao dịch thực thi nguyên tử 100% trên Redis, loại bỏ triệt để xung đột khi nhiều người cùng bấm 1 ghế tại cùng 1 mili-giây.
  * **Hỗ trợ đa dạng loại vé:** Áp dụng khóa giữ chỗ theo thời hạn (TTL) cho cả **Ghế chỉ định (Seated)** và trừ tồn kho vé tức thời cho **Vé tự do/đứng (Unassigned Standing)**.
  * **Dọn dẹp phân tán (ShedLock):** Kết hợp **ShedLock** (`shedlock-spring`) cùng **ReservationCleanupJob** để tự động hoàn trả ghế khi hết hạn giữ chỗ mà không bị chạy trùng lặp khi hệ thống scale nhiều instance.

### 2. Transactional Outbox Pattern & Redis Streams (`job/`)
* **OutboxRelayJob:** Áp dụng **Transactional Outbox Pattern** để đảm bảo tính nhất quán (Eventual Consistency) giữa lưu trữ Database và phát tín hiệu Message / Notification, hạn chế mất mát dữ liệu khi gặp lỗi mạng.
* **RedisStreamRecoveryJob & TicketQueue:** Tận dụng **Redis Streams** cho các tiến trình xử lý vé bất đồng bộ, có cơ chế Recovery tự động quét và khôi phục các tác vụ bị treo (Pending / Dead Letter) để đảm bảo không một giao dịch nào bị thất thoát.

### 3. Bảo mật, Rate Limiting & Phân quyền động phân cấp (Granular RBAC)
* **Hệ thống Rate Limiting 2 lớp (Redis Sliding Window):**
  * **Tầng 1 - Theo Thiết bị / IP ([RateLimit.java](src/main/java/com/example/event/filter/RateLimit.java)):** Bảo vệ các luồng xác thực; chống Brute-Force dò mật khẩu, spam gửi mã OTP qua Email và lạm dụng refresh token.
  * **Tầng 2 - Theo Người dùng ([UserRateLimit.java](src/main/java/com/example/event/filter/UserRateLimit.java)):** Kiểm soát các hành động sau đăng nhập; ngăn chặn bot hoặc người dùng spam tạo đơn thanh toán ảo và hủy đặt chỗ liên tục.
* **Cơ chế xác thực JWT & Refresh Token Rotation (RTR):**
  * Áp dụng kỹ thuật **Refresh Token Rotation** chuẩn bảo mật: Mỗi lần cấp mới Access Token, hệ thống sẽ sinh ra một Refresh Token mới và thu hồi token cũ ngay lập tức.
  * Cơ chế **Phát hiện tái sử dụng Token (Reuse Detection & Token Family)**: Nếu phát hiện Refresh Token cũ đã bị thu hồi gửi lại (dấu hiệu bị đánh cắp / Replay Attack), hệ thống sẽ tự động thu hồi toàn bộ phiên trong Token Family đó để bảo vệ tài khoản.
  * Database chỉ lưu bản băm (Hash) của Refresh Token, đồng thời ràng buộc chặt chẽ với định danh thiết bị (`deviceId`) và cơ chế hủy phiên khi đổi mật khẩu (`tokenVersion`).
* **Xác thực đa kênh:** Đăng nhập truyền thống kèm kích hoạt tài khoản qua OTP Email (Spring Mail) và **Google OAuth2** (`google-api-client`).
* **Cơ chế RBAC (Role-Based Access Control):** Thiết kế động với 2 lớp: **Role** và **Permission** (`EVENT:APPROVE`, `USER:VIEW`, `VOUCHER:CREATE`,...). Quyền hạn được kiểm soát chặt chẽ ở cấp độ từng Controller Method.

### 4. Quy trình Đặt vé, Thanh toán & Phát hành vé khép kín
* **Cổng thanh toán:** Tích hợp trực tiếp cổng **VNPay** với thuật toán HMAC SHA512 bảo mật và xử lý IPN/Callback an toàn.
* **Mã định danh:** Sinh mã đơn hàng và vé độc nhất bằng **ULID** (tối ưu sắp xếp theo thời gian và hiệu năng Indexing hơn UUID v4).
* **Xuất vé điện tử tự động:**
  * Thư viện **ZXing**: Tạo mã QR Code mã hóa chứa thông tin vé để nhân viên quét trực tiếp tại cửa sự kiện.
  * Thư viện **OpenPDF**: Biên tập và xuất file PDF vé sự kiện chuyên nghiệp với bố cục vé tiêu chuẩn, gửi tự động vào email khách hàng sau khi thanh toán thành công.

### 5. Quản lý File An Toàn (Secure Media Storage)
* Sử dụng **Cloudinary SDK** lưu trữ hình ảnh banner, avatar, sơ đồ ghế.
* Xác thực định dạng MIME thật của tệp thông qua **Apache Tika** (`tika-core`), ngăn chặn triệt để lỗ hổng giả mạo đuôi file (File Upload Vulnerabilities).
* Tiến trình **FileCleanupJob** tự động dọn dẹp các tệp mồ côi (Orphaned files) không còn liên kết.

---

## 🛠️ Công Nghệ & Thư Viện Sử Dụng (Tech Stack)

| Phân loại | Công nghệ / Thư viện | Phiên bản | Mục đích sử dụng |
| :--- | :--- | :--- | :--- |
| **Core Platform** | Java | 21 (LTS) | Ngôn ngữ lập trình cốt lõi, tận dụng tính năng hiện đại (Virtual Threads, Pattern Matching) |
| **Framework** | Spring Boot | 3.3.5 | Nền tảng phát triển RESTful API |
| **Security** | Spring Security, JJWT | 0.11.5 | Xác thực Stateless JWT, mã hóa BCrypt, bảo mật Endpoint |
| **Database & ORM** | MySQL, Spring Data JPA, Hibernate | 8.0 | Hệ quản trị cơ sở dữ liệu quan hệ chính & ORM mapping |
| **Cache, Queue & Limit** | Redis, Spring Data Redis | 6.0+ | Bộ nhớ đệm, Rate Limiting 2 lớp (Lua Script), Hàng đợi đặt vé, Virtual Waiting Room, Redis Streams |
| **Cron & Locking** | ShedLock | 6.0.2 | Phân tán khóa cho Scheduler dọn dẹp reservation |
| **Third-Party Integrations** | VNPay, Cloudinary, Gmail SMTP | - | Thanh toán trực tuyến, Lưu trữ ảnh, Gửi email thông báo |
| **Document & Media** | OpenPDF, ZXing, Apache Tika | - | Tạo file PDF vé, Tạo mã QR Code, Thẩm định file an toàn |
| **DevOps & Container** | Docker, Docker Compose | - | Đóng gói và chạy môi trường đa dịch vụ (App + DB + Redis) |

---

## 📂 Cấu Trúc Thư Mục Dự Án (Project Structure)

```text
EventHunting/
├── src/main/java/com/example/event/
│   ├── EventApplication.java               # Main entry point của ứng dụng Spring Boot
│   │
│   ├── component/                          # Async Consumers/Producers & Data Initializer
│   │   ├── DataLoader.java                 # Seed dữ liệu quyền (Permission), Role và tài khoản Admin
│   │   ├── TicketEmailProducer.java / Consumer.java      # Hàng đợi gửi email vé điện tử (PDF/QR)
│   │   ├── AuthVerifyEmailProducer.java / Consumer.java  # Hàng đợi gửi mã OTP kích hoạt tài khoản
│   │   ├── AuthForgotPasswordProducer.java / Consumer.java # Hàng đợi gửi mã đặt lại mật khẩu
│   │   └── ReservationCodeMigrationRunner.java
│   │
│   ├── config/                             # Cấu hình hệ thống & tích hợp bên thứ ba
│   │   ├── security/                       # Cấu hình Spring Security 6 & Stateless JWT
│   │   │   ├── config/SecurityConfig.java  # Security Filter Chain, CORS, Authorization rules
│   │   │   ├── jwt/JwtTokenFilter.java     # Bộ lọc xác thực JWT Access Token trên từng request
│   │   │   ├── jwt/JwtUtils.java           # Tạo, giải mã và thẩm định JWT
│   │   │   ├── jwt/RefreshTokenUtils.java  # Sinh & hash Refresh Token cho cơ chế Rotation
│   │   │   └── SecurityUtils.java          # Utility lấy thông tin User hiện tại từ SecurityContext
│   │   ├── RedisConfig.java                # Cấu hình RedisTemplate, Serializer & Cache
│   │   ├── RedisStreamConfig.java          # Cấu hình Redis Streams cho hàng đợi vé
│   │   ├── ShedLockConfig.java             # Cấu hình khóa phân tán trên JDBC Template
│   │   ├── WebSocketConfig.java            # Cấu hình STOMP WebSocket thông báo thời gian thực
│   │   ├── VNPayConfig.java                # Thông số kết nối cổng thanh toán VNPay Sandbox
│   │   └── CloudinaryConfig.java           # Cấu hình SDK lưu trữ ảnh Cloudinary
│   │
│   ├── filter/                             # Các bộ lọc HTTP Servlet (Filters)
│   │   ├── RateLimit.java                  # Tầng 1: Giới hạn tần suất theo Thiết bị/IP (Redis Lua Script)
│   │   ├── UserRateLimit.java              # Tầng 2: Giới hạn tần suất theo User ID cho API nhạy cảm
│   │   └── MdcFilter.java                  # Gắn Correlation ID vào logging để tracing request
│   │
│   ├── job/                                # Scheduled Tasks & Background Workers
│   │   ├── ReservationCleanupJob.java      # Cron tự động dọn reservation hết hạn (chạy với ShedLock)
│   │   ├── OutboxRelayJob.java             # Quét bảng Outbox phát sự kiện (Transactional Outbox)
│   │   ├── RedisStreamRecoveryJob.java     # Phục hồi tin nhắn pending/lỗi trên Redis Streams
│   │   ├── TicketQueue.java                # Tiến trình tiêu thụ hàng đợi đặt vé giờ cao điểm
│   │   └── FileCleanupJob.java             # Tự động dọn dẹp ảnh mồ côi (Orphaned files)
│   │
│   ├── service/                            # Tầng nghiệp vụ (Interfaces & Implementations)
│   │   ├── LockService.java                # Interface điều phối khóa ghế
│   │   ├── Impl/LockServiceImpl.java       # Thực thi Redis Lua Scripts (Khóa ghế & trừ kho nguyên tử)
│   │   ├── Impl/ReservationServiceImpl.java# Nghiệp vụ đặt chỗ, tính tiền, áp dụng voucher
│   │   ├── Impl/VNPaymentServiceImpl.java  # Tích hợp tạo URL VNPay & xác thực chữ ký IPN callback
│   │   ├── Impl/TicketPdfService.java      # Biên tập và xuất file PDF vé bằng OpenPDF & ZXing
│   │   ├── Impl/AuthServiceImpl.java       # Đăng nhập, đăng ký OTP, Refresh Token Rotation
│   │   └── ...                             # Các services: Event, Show, Ticket, Voucher, User...
│   │
│   ├── controller/                         # Tầng REST API Endpoints (17 Controllers)
│   │   ├── AuthController.java             # Đăng nhập, đăng ký, refresh-token, verify OTP
│   │   ├── EventController.java            # Tìm kiếm, tạo, sửa, duyệt sự kiện
│   │   ├── ShowController.java             # Quản lý suất diễn & trả sơ đồ ghế (Seat Map)
│   │   ├── ReservationController.java      # Giữ chỗ, hủy giữ chỗ, xem tóm tắt đơn đặt vé
│   │   ├── PaymentController.java          # Tạo giao dịch thanh toán VNPay & callback
│   │   ├── TicketController.java           # Lấy vé của tôi, tải PDF vé, quét QR check-in
│   │   ├── AnalyticsController.java        # Dashboard thống kê doanh thu và báo cáo
│   │   └── ...                             # Category, Voucher, Role, Permission, File...
│   │
│   ├── entity/                             # Mô hình thực thể cơ sở dữ liệu (JPA Entities)
│   │   ├── User.java, Role.java, Permission.java
│   │   ├── Event.java, Show.java, Seat.java, TicketTier.java, TicketType.java
│   │   ├── Reservation.java, ReservationItem.java, Ticket.java, Payment.java
│   │   ├── Session.java                    # Lưu phiên Refresh Token và Token Family chống tấn công
│   │   └── OutboxMessage.java              # Bảng Outbox lưu trữ sự kiện cần phát
│   │
│   ├── repository/                         # Spring Data JPA Repositories
│   ├── dto/                                # Data Transfer Objects (request/ và response/)
│   ├── exception/                          # AppException & GlobalExceptionHandler (Chuẩn RFC 7807)
│   ├── specification/                     # Dynamic Search Criteria cho bộ lọc sự kiện
│   ├── constant/                           # Các Enum trạng thái & mã lỗi ErrorCode
│   ├── mapper/                             # ModelMapper DTO <-> Entity
│   └── util/                               # VNPayUtil (HMAC SHA512), ReservationCodeGenerator, FileUtil...
│
├── src/main/resources/
│   ├── application.yaml                    # Nạp cấu hình từ biến môi trường .env
│   ├── application-local.yaml              # Cấu hình môi trường phát triển cục bộ
│   ├── application-prod.yaml               # Cấu hình môi trường Production
│   └── fonts/arial.ttf                     # Phông chữ Unicode nhúng khi xuất PDF vé tiếng Việt
│
├── database/
│   └── event_db.sql                        # Script SQL khởi tạo toàn bộ cấu trúc bảng & nạp dữ liệu mẫu
│
├── Dockerfile                              # Multi-stage Dockerfile đóng gói ứng dụng Spring Boot
├── docker-compose.yml                      # File cấu hình chạy trọn cụm App + MySQL 8 + Redis
├── pom.xml                                 # Khai báo các thư viện phụ thuộc (Maven)
└── .env.example                            # Mẫu biến môi trường cần thiết
```

---

## ⚙️ Hướng Dẫn Cài Đặt & Chạy Cục Bộ (Setup & Installation)

### 1. Yêu cầu tiên quyết (Prerequisites)
* **Java:** JDK 21 trở lên.
* **Build Tool:** Maven 3.9+ (hoặc dùng `mvnw` đi kèm).
* **Database:** MySQL Server 8.0+
* **Cache & Memory:** Redis 6.0+
* *(Tùy chọn)* **Docker & Docker Compose** nếu muốn chạy trọn gói container.

---

### 2. Thiết lập Biến Môi Trường (.env)
Sao chép tệp `.env.example` thành `.env` tại thư mục gốc backend:

```bash
cp .env.example .env
```

Cấu hình các thông số phù hợp với máy của bạn:
```properties
# Application & Frontend URLs
APP_API_BASE_URL=http://localhost:8080/api/v1
FRONTEND_URL=http://localhost:5173

# Cấu hình Cơ sở dữ liệu MySQL
DB_URL=jdbc:mysql://localhost:3306/event_db?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=your_database_password

# Cấu hình Mail SMTP (Gửi OTP kích hoạt tài khoản & file PDF vé điện tử)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_gmail_app_password
MAIL_FROM=your_email@gmail.com

# Cấu hình Google OAuth2 (Đăng nhập Google)
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret

# Cấu hình Redis (Cache, Rate Limit, Waiting Room, Streams)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Cấu hình Hàng đợi mua vé (Giới hạn số người mua vé đồng thời mỗi suất diễn)
TICKET_QUEUE_MAX_BUYING=50

# Cấu hình JWT (Sinh chuỗi ngẫu nhiên an toàn bằng: openssl rand -base64 64)
JWT_SECRET=your_base64_secret_at_least_256_bits
JWT_ACCESS_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=864000000
JWT_VERIFY_EXPIRATION=300000

# Cấu hình Cloudinary (Lưu trữ ảnh sự kiện & banner)
CLOUDINARY_CLOUD_NAME=your_cloudinary_cloud_name
CLOUDINARY_API_KEY=your_cloudinary_api_key
CLOUDINARY_API_SECRET=your_cloudinary_api_secret

# Cấu hình VNPAY Sandbox (Cổng thanh toán trực tuyến)
VNPAY_TMN_CODE=your_vnpay_tmn_code
VNPAY_HASH_SECRET=your_vnpay_hash_secret
VNPAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=http://localhost:8080/api/v1/payments/vnpay-callback
```

---

### 3. Khởi tạo Cơ Sở Dữ Liệu
1. Mở MySQL Workbench hoặc terminal MySQL và tạo schema:
   ```sql
   CREATE DATABASE event_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. Import script cấu trúc và dữ liệu mẫu:
   ```bash
   mysql -u root -p event_db < database/event_db.sql
   ```

---

### 4. Khởi chạy Ứng dụng

#### Cách 1: Khởi chạy bằng Maven (Local Development)
```bash
# Cài đặt thư viện và build
mvn clean install -DskipTests

# Khởi chạy Spring Boot
mvn spring-boot:run
```
Ứng dụng sẽ khởi chạy tại: `http://localhost:8080`

#### Cách 2: Khởi chạy nhanh bằng Docker Compose
Nếu máy đã cài Docker:
```bash
docker compose up -d
```
Docker Compose sẽ tự động khởi dựng cụm dịch vụ:
* `mysql-container` (Port 3306)
* `my-redis` (Port 6379)
* `event-app-container` (Port 8080)

---

## 🧪 Kiểm Thử & Kiểm Định (Testing)
```bash
# Chạy toàn bộ test suites
mvn test
```

---

## 👨‍💻 Tác Giả (Author)
* **GitHub:** [@HoUy260102](https://github.com/HoUy260102)
* **Dự án liên quan:**
  * Backend: [event-hunting-be](https://github.com/HoUy260102/event-hunting-be)
  * Frontend: [event-hunting-fe](https://github.com/HoUy260102/event-hunting-fe)
