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
    SHOW_CANCELLED(HttpStatus.GONE, "Sự kiện đã bị hủy. Vui lòng kiểm tra thông báo để biết thêm chi tiết về việc hoàn tiền."),
    SHOW_POSTPONED(HttpStatus.BAD_REQUEST, "Sự kiện đang tạm hoãn và không tiếp nhận đặt vé mới tại thời điểm này."),
    SHOW_ENDED(HttpStatus.BAD_REQUEST, "Sự kiện này đã kết thúc. Bạn không thể đặt vé được nữa."),
    SHOW_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "Hiện tại sự kiện này chưa sẵn sàng để mở bán vé. Vui lòng quay lại sau."),

    EVENT_VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu sự kiện không hợp lệ."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy được event."),

    TICKET_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy được ticket type."),
    TICKET_TYPE_IN_ACTIVE(HttpStatus.BAD_REQUEST, "Loại vé này đã ngưng hoạt động."),
    TICKET_TYPE_SUSPENDED(HttpStatus.BAD_REQUEST, "Loại vé đang tạm hoãn và không tiếp nhận đặt vé mới tại thời điểm này."),
    TICKET_TYPE_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "Hiện tại loại vé này chưa sẵn sàng để mở bán vé. Vui lòng quay lại sau."),
    TICKET_TYPE_SOLD_OUT(HttpStatus.BAD_REQUEST, ""),

    TICKET_TIER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy được ticket tier."),
    TICKET_TIER_IN_ACTIVE(HttpStatus.BAD_REQUEST, "Hạng vé này đã ngưng hoạt động."),
    TICKET_TIER_SUSPENDED(HttpStatus.BAD_REQUEST, "Hạng vé đang tạm hoãn và không tiếp nhận đặt vé mới tại thời điểm này."),
    TICKET_TIER_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "Hiện tại hạng vé này chưa sẵn sàng để mở bán vé. Vui lòng quay lại sau."),
    TICKET_TIER_SOLD_OUT(HttpStatus.BAD_REQUEST, ""),

    TOTAL_QUANTITY_LESS_THAN_SOLD(HttpStatus.BAD_REQUEST, "Tổng số lượng vé không được nhỏ hơn số lượng vé đã bán"),
    LIMIT_QUANTITY_LESS_THAN_SOLD(HttpStatus.BAD_REQUEST, "Tổng số lượng vé giới hạn không được nhỏ hơn số lượng vé đã bán"),
    SEATMAP_UPDATE_FORBIDDEN(HttpStatus.BAD_REQUEST, "Không thể thay đổi sơ đồ ghế vì sự kiện đã có vé được bán hoặc đã được cấu hình."),
    SEATMAP_TYPE_UPDATE_FORBIDDEN(HttpStatus.BAD_REQUEST, "Không thể thay đổi type sơ đồ ghế vì sự kiện đã có vé được bán hoặc đã được cấu hình."),
    SEATING_TYPE_UPDATE_FORBIDDEN(HttpStatus.BAD_REQUEST, "Không thể thay đổi seating type vì sự kiện đã có vé được bán hoặc đã được cấu hình."),

    RESERVATION_ALREADY_CANCELLED(HttpStatus.BAD_REQUEST, "Giữ chỗ này đã hủy."),
    RESERVATION_EXPIRED(HttpStatus.BAD_REQUEST, "Giữ chỗ này đã hết hạn."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy reservation."),
    RESERVATION_ALREADY_PAID(HttpStatus.BAD_REQUEST, "Reservation này đã được thanh toán."),
    RESERVATION_QUANTITY_EXCEEDED(HttpStatus.BAD_REQUEST, "Số lượng vé vượt quá giới hạn tối đa cho phép của mỗi đơn hàng."),
    RESERVATION_QUANTITY_MINIMUM_NOT_MET(HttpStatus.BAD_REQUEST, "Số lượng vé chưa đạt mức tối thiểu yêu cầu cho mỗi đơn hàng."),

    TICKET_TYPE_FULL(HttpStatus.BAD_REQUEST, "Loại vé đã hết số lượng tổng cho phép."),
    TICKET_TIER_FULL(HttpStatus.BAD_REQUEST, "Hạng vé đã đạt giới hạn tối đa cho khu vực này."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "Số lượng vé không hợp lệ."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "Số tiền không hợp lệ."),

    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy ghế."),
    SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, "Ghế đã có người đặt hoặc đang được giữ."),

    SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi do hệ thống.");

    private final String message;
    private final HttpStatus httpStatus;
    ErrorCode(HttpStatus httpStatus, String message) {
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
