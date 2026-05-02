package com.file.conversion.controller;

import com.file.conversion.model.FileInfo;
import com.file.conversion.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<List<FileInfo>> upload(@RequestParam("files") List<MultipartFile> files) throws IOException {
        List<FileInfo> infos = fileStorageService.storeMultiple(files);
        return ResponseEntity.ok(infos);
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<FileInfo> getFileInfo(@PathVariable String fileId) {
        return fileStorageService.get(fileId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String fileId) {
        var infoOpt = fileStorageService.get(fileId);
        if (infoOpt.isEmpty()) return ResponseEntity.notFound().build();

        Resource resource = fileStorageService.loadAsResource(fileId);
        if (resource == null) return ResponseEntity.notFound().build();

        FileInfo info = infoOpt.get();
        String contentType = "application/octet-stream";
        try {
            contentType = Files.probeContentType(resource.getFile().toPath());
        } catch (IOException ignored) {
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + info.getOriginalName() + "\"")
                .body(resource);
    }

    @GetMapping("/preview/{fileId}")
    public ResponseEntity<String> preview(@PathVariable String fileId) {
        return fileStorageService.get(fileId)
                .map(info -> {
                    try {
                        // Only allow text-based content types for preview
                        String mime = info.getMimeType();
                        if (mime != null && !mime.startsWith("text/")
                                && !mime.contains("json")
                                && !mime.contains("csv")
                                && !mime.contains("xml")
                                && !mime.contains("javascript")
                                && !"application/octet-stream".equals(mime)) {
                            return ResponseEntity.<String>badRequest()
                                    .body("This file type cannot be previewed as text");
                        }

                        String content = Files.readString(java.nio.file.Path.of(info.getStoredPath()));
                        return ResponseEntity.ok()
                                .contentType(MediaType.TEXT_PLAIN)
                                .body(content);
                    } catch (IOException e) {
                        return ResponseEntity.<String>badRequest()
                                .body("File cannot be previewed as text");
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(@PathVariable String fileId) {
        return fileStorageService.delete(fileId)
                .map(info -> ResponseEntity.noContent().<Void>build())
                .orElse(ResponseEntity.notFound().build());
    }
}
