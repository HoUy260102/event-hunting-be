package com.example.event.constant;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token đã hết hạn."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token không hợp lệ."),
    TOKEN_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "Token có chữ ký không hợp lệ."),
    TOKEN_MALFORMED(HttpStatus.BAD_REQUEST, "Token bị chỉnh sửa (malformed)."),
    TOKEN_UNSUPPORTED(HttpStatus.BAD_REQUEST, "Loại token không được hỗ trợ."),
    TOKEN_TYPE_INVALID(HttpStatus.FORBIDDEN, "Token type không hợp lệ, cần ACCESS token."),
    TOKEN_ILLEGAL_ARGUMENT(HttpStatus.BAD_REQUEST, "Token rỗng hoặc invalid."),

    LOGIN_BLOCKED(HttpStatus.TOO_MANY_REQUESTS, "Tài khoản tạm thời bị khóa do đăng nhập thất bại nhiều lần."),

    PERMISSION_EXISTS(HttpStatus.BAD_REQUEST, "Permission này đã tồn tại."),
    PERMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy permission."),

    ROLE_EXISTS(HttpStatus.BAD_REQUEST, "Role này đã tồn tại."),
    ROLE_NOT_FOUND(HttpStatus.BAD_REQUEST, "Role này không tồn tại."),

    USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "User này không tồn tại."),
    USER_EXISTS(HttpStatus.BAD_REQUEST, "User này đã tồn tại."),
    USER_VERIFIED(HttpStatus.BAD_REQUEST, "User này đã xác thực."),
    USER_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "User này chưa được xác thực."),

    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST,"Mật khẩu xác nhận không khớp."),

    INVALID_FILE_EXTENSION(HttpStatus.BAD_REQUEST, "Đuôi file không được hỗ trợ."),
    INVALID_FILE_MIME_TYPE(HttpStatus.BAD_REQUEST, "Nội dung file không hợp lệ."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "Kích thước file vượt quá giới hạn."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy file."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "Kiểu file không hợp lệ."),
    UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi xảy ra trong quá trình upload lên Cloudinary."),

    CATEGORY_EXISTS(HttpStatus.BAD_REQUEST, "Tên hoặc đường dẫn danh mục đã tồn tại."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Danh mục không tồn tại trên hệ thống."),
    CATEGORY_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "Danh mục này đã nằm trong thùng rác."),
    CATEGORY_NOT_IN_TRASH(HttpStatus.BAD_REQUEST, "Danh mục hiện không ở trạng thái chờ khôi phục."),
    CATEGORY_STATUS_INVALID(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ."),

    SHOWS_VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu suất diễn không hợp lệ."),
    SHOW_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy được show."),
    INVALID_EVENT_SHOW_RELATION(HttpStatus.BAD_REQUEST, "Suất diễn không thuộc sự kiện này."),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "Chuyển đổi trạng thái không hợp lệ"),
    SHOW_ALREADY_CANCELLED(HttpStatus.BAD_REQUEST, "Suất diễn đã bị hủy, không thể chỉnh sửa"),

    EVENT_VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu sự kiện không hợp lệ."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy được event."),

    TICKET_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy được ticket type."),

    TICKET_TIER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy được ticket tier."),

    TOTAL_QUANTITY_LESS_THAN_SOLD(HttpStatus.BAD_REQUEST, "Tổng số lượng vé không được nhỏ hơn số lượng vé đã bán"),
    LIMIT_QUANTITY_LESS_THAN_SOLD(HttpStatus.BAD_REQUEST, "Tổng số lượng vé giới hạn không được nhỏ hơn số lượng vé đã bán"),
    SEATMAP_UPDATE_FORBIDDEN(HttpStatus.BAD_REQUEST, "Không thể thay đổi sơ đồ ghế vì sự kiện đã có vé được bán hoặc đã được cấu hình."),
    SEATMAP_TYPE_UPDATE_FORBIDDEN(HttpStatus.BAD_REQUEST, "Không thể thay đổi type sơ đồ ghế vì sự kiện đã có vé được bán hoặc đã được cấu hình."),
    SEATING_TYPE_UPDATE_FORBIDDEN(HttpStatus.BAD_REQUEST, "Không thể thay đổi seating type vì sự kiện đã có vé được bán hoặc đã được cấu hình.");

    private final String message;
    private final HttpStatus httpStatus;
    ErrorCode(HttpStatus httpStatus, String message) {
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
