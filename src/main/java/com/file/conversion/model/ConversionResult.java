package com.file.conversion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionResult {
    private boolean success;
    private String fileName;
    private String resultFilePath;
    private String downloadUrl;
    private String previewContent;
    private String errorMessage;
}
