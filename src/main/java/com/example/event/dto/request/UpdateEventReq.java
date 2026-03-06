package com.example.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateEventReq {
    @NotBlank(message = "Vui lòng upload hoặc chọn ảnh poster")
    private String posterId;

    @NotBlank(message = "Vui lòng upload hoặc chọn ảnh banner")
    private String bannerId;

    @NotBlank(message = "Tên sự kiện không được để trống")
    @Size(min = 5, max = 100, message = "Tên sự kiện từ 5 đến 100 ký tự")
    private String name;

    @NotBlank(message = "Vui lòng nhập tên địa điểm")
    private String location;

    @NotBlank(message = "Vui lòng chọn Tỉnh/Thành")
    private String provinceId;

    @NotBlank(message = "Vui lòng chọn thể loại")
    private String categoryId;

    private String descriptionHtml;
    private String descriptionText;
    private List<String> mediaIds;

    @NotBlank(message = "Vui lòng chọn ảnh logo tổ chức")
    private String organizerLogoId;

    @NotBlank(message = "Vui lòng nhập tên ban tổ chức")
    private String organizerName;

    @NotBlank(message = "Thông tin ban tổ chức không được để trống")
    @Size(min = 10, max = 500, message = "Thông tin ban tổ chức từ 10 đến 500 ký tự")
    private String organizerInfo;
}
