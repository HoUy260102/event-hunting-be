package com.example.event.dto.request;

import com.example.event.constant.FileFolder;
import com.example.event.constant.FileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InitUploadReq {
    @NotBlank(message = "Tên file không được để trống")
    private String fileName;

    @NotNull(message = "Loại file không được để trống")
    private FileType fileType;

    @NotNull(message = "Thư mục không được để trống")
    private FileFolder folder;

    @NotNull(message = "Dung lượng file không được để trống")
    private Long fileSize;

    @NotBlank(message = "Header bytes không được để trống")
    private String headerBytes;
}

