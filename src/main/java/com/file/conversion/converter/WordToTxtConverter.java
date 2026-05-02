package com.file.conversion.converter;

import com.file.conversion.model.ConversionResult;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class WordToTxtConverter implements Converter {

    private static final Logger log = LoggerFactory.getLogger(WordToTxtConverter.class);

    @Override
    public Set<FormatPair> supportedFormats() {
        return Set.of(
                FormatPair.of("doc", "txt"),
                FormatPair.of("docx", "txt")
        );
    }

    @Override
    public ConversionResult convert(File source, String targetFormat) {
        String name = source.getName().toLowerCase();
        try {
            String text;
            if (name.endsWith(".docx")) {
                text = extractDocx(source);
            } else {
                text = extractDoc(source);
            }

            String outputName = source.getName().replaceAll("(?i)\\.docx?$", ".txt");
            File outputFile = new File(source.getParent(), outputName);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                writer.write(text);
            }

            return ConversionResult.builder()
                    .success(true)
                    .fileName(outputName)
                    .resultFilePath(outputFile.getAbsolutePath())
                    .previewContent(text.length() <= 3000 ? text : text.substring(0, 3000) + "...")
                    .build();
        } catch (Exception e) {
            log.error("Word to TXT failed", e);
            return ConversionResult.builder()
                    .success(false).errorMessage("Word 转 TXT 失败：" + e.getMessage()).build();
        }
    }

    private String extractDocx(File source) throws IOException {
        try (InputStream is = new FileInputStream(source);
             XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String extractDoc(File source) throws IOException {
        try (InputStream is = new FileInputStream(source);
             HWPFDocument doc = new HWPFDocument(is);
             WordExtractor extractor = new WordExtractor(doc)) {
            return extractor.getText();
        }
    }
}
