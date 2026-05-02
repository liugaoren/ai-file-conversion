package com.file.conversion.task;

import com.file.conversion.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FileCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupTask.class);

    private final FileStorageService fileStorageService;

    public FileCleanupTask(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Scheduled(fixedRateString = "${app.file.cleanup-interval-ms:300000}")
    public void cleanupExpiredFiles() {
        fileStorageService.cleanExpired();
        log.debug("Expired file cleanup completed");
    }
}
