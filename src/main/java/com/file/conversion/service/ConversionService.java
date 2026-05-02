package com.file.conversion.service;

import com.file.conversion.converter.Converter;
import com.file.conversion.converter.ConverterRegistry;
import com.file.conversion.model.ConversionResult;
import com.file.conversion.model.FileInfo;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ConversionService {

    private final ConverterRegistry converterRegistry;
    private final FileStorageService fileStorageService;

    public ConversionService(ConverterRegistry converterRegistry, FileStorageService fileStorageService) {
        this.converterRegistry = converterRegistry;
        this.fileStorageService = fileStorageService;
    }

    public ConversionResult convert(FileInfo sourceFile, String targetFormat) {
        String sourceExt = fileStorageService.extractExtension(sourceFile.getOriginalName());
        File src = new File(sourceFile.getStoredPath());

        Optional<Converter> converterOpt = converterRegistry.findConverter(sourceExt, targetFormat);
        if (converterOpt.isEmpty()) {
            return ConversionResult.builder()
                    .success(false)
                    .errorMessage("No converter available for " + sourceExt + " → " + targetFormat)
                    .build();
        }

        ConversionResult result = converterOpt.get().convert(src, targetFormat);
        if (result.isSuccess() && result.getResultFilePath() != null) {
            File outputFile = new File(result.getResultFilePath());
            FileInfo outInfo = FileInfo.builder()
                    .id(UUID.randomUUID().toString())
                    .originalName(result.getFileName())
                    .storedPath(outputFile.getAbsolutePath())
                    .size(outputFile.length())
                    .mimeType(detectMimeType(result.getFileName()))
                    .uploadTime(LocalDateTime.now())
                    .expiredAt(LocalDateTime.now().plusMinutes(60))
                    .build();
            fileStorageService.registerFile(outInfo);
            result.setDownloadUrl("/api/files/download/" + outInfo.getId());
        }

        return result;
    }

    public List<ConversionResult> batchConvert(List<FileInfo> sourceFiles, String targetFormat) {
        List<ConversionResult> results = new ArrayList<>();
        for (FileInfo file : sourceFiles) {
            try {
                results.add(convert(file, targetFormat));
            } catch (Exception e) {
                results.add(ConversionResult.builder()
                        .success(false)
                        .fileName(file.getOriginalName())
                        .errorMessage("Unexpected error: " + e.getMessage())
                        .build());
            }
        }
        return results;
    }

    private String detectMimeType(String filename) {
        String ext = fileStorageService.extractExtension(filename);
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "json" -> "application/json";
            case "csv" -> "text/csv";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }
}
