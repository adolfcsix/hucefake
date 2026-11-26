# Backend HUCE

Java 21, Spring Boot, Spring Security, Spring JDBC, Spring Session JDBC, Flyway và PostgreSQL.

- `src/main/java/vn/huce/`: API, service nghiệp vụ, giải thuật di truyền, HUCEChat, bảo mật và CLI.
- `src/main/resources/db/migration/`: migration PostgreSQL.
- `src/main/resources/db/demo/`: danh mục mẫu, chỉ nạp trong chế độ demo.
- `tests/`: kiểm thử HTTP tích hợp chạy bằng Node.js như một công cụ kiểm thử, không phải backend.
- `pom.xml`: Maven.

Xem README gốc để cài đặt, cấu hình và chạy. Không dùng database Express cũ trực tiếp; cần sao lưu và quy trình chuyển dữ liệu riêng.
