package com.file.conversion.service;

import com.file.conversion.model.FileInfo;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileStorageService {

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    @Value("${app.file.expiration-minutes:60}")
    private int expirationMinutes;

    private Path uploadPath;
    private final Map<String, FileInfo> fileStore = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() throws IOException {
        uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);
    }

    public FileInfo store(MultipartFile file) throws IOException {
        String id = UUID.randomUUID().toString();
        String originalName = file.getOriginalFilename();
        if (originalName == null) originalName = "unknown";

        // Sanitize filename
        String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String storedName = id + "_" + safeName;
        Path targetPath = uploadPath.resolve(storedName);

        file.transferTo(targetPath.toFile());

        FileInfo info = FileInfo.builder()
                .id(id)
                .originalName(originalName)
                .storedPath(targetPath.toString())
                .size(file.getSize())
                .mimeType(file.getContentType())
                .uploadTime(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusMinutes(expirationMinutes))
                .build();

        fileStore.put(id, info);
        return info;
    }

    public List<FileInfo> storeMultiple(List<MultipartFile> files) throws IOException {
        List<FileInfo> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(store(file));
        }
        return results;
    }

    public Optional<FileInfo> get(String id) {
        return Optional.ofNullable(fileStore.get(id));
    }

    public Resource loadAsResource(String id) {
        FileInfo info = fileStore.get(id);
        if (info == null) return null;
        Path file = Paths.get(info.getStoredPath());
        if (Files.exists(file)) {
            return new FileSystemResource(file);
        }
        return null;
    }

    public Optional<FileInfo> delete(String id) {
        FileInfo info = fileStore.remove(id);
        if (info != null) {
            try {
                Files.deleteIfExists(Paths.get(info.getStoredPath()));
            } catch (IOException ignored) {
            }
        }
        return Optional.ofNullable(info);
    }

    public void cleanExpired() {
        LocalDateTime now = LocalDateTime.now();
        List<String> expired = fileStore.entrySet().stream()
                .filter(e -> e.getValue().getExpiredAt().isBefore(now))
                .map(Map.Entry::getKey)
                .toList();
        expired.forEach(this::delete);
    }

    /**
     * 将已存在的文件（如转换输出）注册到存储跟踪器中。
     */
    public FileInfo registerFile(FileInfo info) {
        fileStore.put(info.getId(), info);
        return info;
    }

    public String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
