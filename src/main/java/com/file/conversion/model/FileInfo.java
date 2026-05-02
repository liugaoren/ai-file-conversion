package com.file.conversion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {
    private String id;
    private String originalName;
    private String storedPath;
    private long size;
    private String mimeType;
    private LocalDateTime uploadTime;
    private LocalDateTime expiredAt;
}
