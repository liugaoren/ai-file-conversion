package com.file.conversion.service;

import com.file.conversion.model.ConversionResult;
import com.file.conversion.model.FileInfo;
import com.file.conversion.util.ZipUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BatchConversionService {

    private final ConversionService conversionService;
    private final FileStorageService fileStorageService;

    public BatchConversionService(ConversionService conversionService, FileStorageService fileStorageService) {
        this.conversionService = conversionService;
        this.fileStorageService = fileStorageService;
    }

    public ConversionResult batchConvertAndZip(List<FileInfo> sourceFiles, String targetFormat) {
        List<ConversionResult> convertResults = conversionService.batchConvert(sourceFiles, targetFormat);
        List<File> successFiles = new ArrayList<>();

        for (ConversionResult result : convertResults) {
            if (result.isSuccess() && result.getDownloadUrl() != null) {
                // Extract file ID from download URL
                String fileId = result.getDownloadUrl().substring(
                        result.getDownloadUrl().lastIndexOf('/') + 1);
                fileStorageService.get(fileId).ifPresent(info ->
                        successFiles.add(new File(info.getStoredPath())));
            }
        }

        if (successFiles.isEmpty()) {
            return ConversionResult.builder()
                    .success(false)
                    .errorMessage("所有转换均失败，没有可打包的文件")
                    .build();
        }

        try {
            File zipFile = ZipUtils.createZip(
                    successFiles,
                    "batch-conversion-" + System.currentTimeMillis() + ".zip",
                    new File(fileStorageService.get(sourceFiles.get(0).getId())
                            .orElseThrow().getStoredPath()).getParentFile()
            );

            FileInfo zipInfo = FileInfo.builder()
                    .id(UUID.randomUUID().toString())
                    .originalName(zipFile.getName())
                    .storedPath(zipFile.getAbsolutePath())
                    .size(zipFile.length())
                    .mimeType("application/zip")
                    .uploadTime(LocalDateTime.now())
                    .expiredAt(LocalDateTime.now().plusMinutes(60))
                    .build();
            fileStorageService.registerFile(zipInfo);

            // 构建摘要
            int successCount = (int) convertResults.stream().filter(ConversionResult::isSuccess).count();
            int failCount = convertResults.size() - successCount;
            StringBuilder summary = new StringBuilder();
            summary.append("Batch conversion complete: ")
                    .append(successCount).append(" succeeded, ")
                    .append(failCount).append(" failed.\n");
            for (ConversionResult r : convertResults) {
                String status = r.isSuccess() ? "✅" : "❌";
                summary.append(status).append(" ").append(r.getFileName())
                        .append(r.isSuccess() ? "" : ": " + r.getErrorMessage())
                        .append("\n");
            }
            summary.append("\nDownload ZIP: /api/files/download/").append(zipInfo.getId());

            return ConversionResult.builder()
                    .success(true)
                    .fileName(zipFile.getName())
                    .downloadUrl("/api/files/download/" + zipInfo.getId())
                    .previewContent(summary.toString())
                    .build();
        } catch (Exception e) {
            return ConversionResult.builder()
                    .success(false)
                    .errorMessage("创建 ZIP 文件失败：" + e.getMessage())
                    .build();
        }
    }
}
