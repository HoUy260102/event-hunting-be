package com.example.event.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UploadSignatureResponse {
    private String fileId;
    private String publicId;
    private String signature;
    private long timestamp;
    private String apiKey;
    private String cloudName;
    private String folder;
    private String allowedFormats;
    private String resourceType;
}

