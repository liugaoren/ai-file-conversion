package com.file.conversion.util;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

    public static File createZip(List<File> files, String zipName, File parentDir) throws IOException {
        File zipFile = new File(parentDir, zipName);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            byte[] buffer = new byte[8192];
            for (File file : files) {
                if (file.exists()) {
                    ZipEntry entry = new ZipEntry(file.getName());
                    zos.putNextEntry(entry);
                    try (FileInputStream fis = new FileInputStream(file)) {
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                    }
                    zos.closeEntry();
                }
            }
        }
        return zipFile;
    }

    public static File createZip(List<File> files, String zipName, Path parentDir) throws IOException {
        return createZip(files, zipName, parentDir.toFile());
    }
}
