package com.file.conversion.tools;

import com.file.conversion.model.ConversionResult;
import com.file.conversion.model.FileInfo;
import com.file.conversion.service.BatchConversionService;
import com.file.conversion.service.ConversionService;
import com.file.conversion.service.FileStorageService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversionTools {

    private final ConversionService conversionService;
    private final BatchConversionService batchConversionService;
    private final FileStorageService fileStorageService;

    public ConversionTools(ConversionService conversionService,
                           BatchConversionService batchConversionService,
                           FileStorageService fileStorageService) {
        this.conversionService = conversionService;
        this.batchConversionService = batchConversionService;
        this.fileStorageService = fileStorageService;
    }

    @Tool(description = "将单个已上传的文件转换为指定的目标格式。" +
            "支持的格式：Word(doc/docx)→pdf/txt、Markdown(md)→pdf/html、PNG(png)→jpg/jpeg、" +
            "Excel(xls/xlsx)→json/csv、CSV→json、JSON→csv、HTML→pdf")
    public String convertSingleFile(
            @ToolParam(description = "已上传文件的文件 ID") String fileId,
            @ToolParam(description = "目标格式：pdf、json、csv、jpg、jpeg") String targetFormat) {

        FileInfo fileInfo = fileStorageService.get(fileId).orElse(null);
        if (fileInfo == null) {
            return "Error: File not found with ID: " + fileId;
        }

        ConversionResult result = conversionService.convert(fileInfo, targetFormat);
        if (result.isSuccess()) {
            String dlFileId = result.getDownloadUrl().substring(
                    result.getDownloadUrl().lastIndexOf('/') + 1);
            return "__DOWNLOAD__:" + dlFileId + "\nConversion successful! File: " + result.getFileName()
                    + (result.getPreviewContent() != null ? "\n\nPreview:\n" + result.getPreviewContent() : "");
        } else {
            return "Conversion failed: " + result.getErrorMessage();
        }
    }

    @Tool(description = "将多个已上传的文件转换为同一目标格式，并打包为 ZIP 文件供下载。" +
            "当用户提到「批量」或要求转换多个文件时使用。")
    public String batchConvertAndZip(
            @ToolParam(description = "要转换的文件 ID 列表") List<String> fileIds,
            @ToolParam(description = "所有文件的目标格式：pdf、json、csv、jpg、jpeg") String targetFormat) {

        List<FileInfo> files = fileIds.stream()
                .map(fileStorageService::get)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        if (files.isEmpty()) {
            return "Error: No valid files found for batch conversion";
        }

        if (files.size() < fileIds.size()) {
            return "Warning: " + (fileIds.size() - files.size()) + " file(s) not found. Converting " + files.size() + " available file(s).\n";
        }

        ConversionResult result = batchConversionService.batchConvertAndZip(files, targetFormat);
        if (result.isSuccess()) {
            String marker = "";
            if (result.getDownloadUrl() != null) {
                String dlFileId = result.getDownloadUrl().substring(
                        result.getDownloadUrl().lastIndexOf('/') + 1);
                marker = "__DOWNLOAD__:" + dlFileId + "\n";
            }
            return marker + (result.getPreviewContent() != null ? result.getPreviewContent()
                    : "Batch conversion complete! Download ZIP: /api/files/download/" + (result.getDownloadUrl() != null
                            ? result.getDownloadUrl().substring(result.getDownloadUrl().lastIndexOf('/') + 1) : "unknown"));
        } else {
            return "Batch conversion failed: " + result.getErrorMessage();
        }
    }

    @Tool(description = "查看所有支持的文件格式转换类型")
    public String getSupportedConversions() {
        return """
                支持的格式转换：
                📝 Word (.doc/.docx) → PDF, TXT
                📋 Markdown (.md) → PDF, HTML
                🖼️ PNG → JPG/JPEG
                📊 Excel (.xls/.xlsx) → JSON, CSV
                📑 CSV → JSON
                📄 JSON → CSV
                🌐 HTML → PDF

                使用方式：上传文件后，告诉我你要转换的格式即可。""";
    }
}
