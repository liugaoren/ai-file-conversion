package com.file.conversion.converter;

import com.file.conversion.model.ConversionResult;
import com.file.conversion.util.ChineseFontUtils;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.Set;

@Component
public class HtmlToPdfConverter implements Converter {

    private static final Logger log = LoggerFactory.getLogger(HtmlToPdfConverter.class);

    @Override
    public Set<FormatPair> supportedFormats() {
        return Set.of(FormatPair.of("html", "pdf"), FormatPair.of("htm", "pdf"));
    }

    @Override
    public ConversionResult convert(File source, String targetFormat) {
        try {
            String html = readFile(source);

            // Ensure full HTML document
            if (!html.trim().toLowerCase().startsWith("<!doctype") && !html.trim().toLowerCase().startsWith("<html")) {
                html = """
                        <!DOCTYPE html>
                        <html xmlns="http://www.w3.org/1999/xhtml"><head>
                        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
                        <style>
                            body { font-family: 'PingFang SC', 'STHeiti', 'Microsoft YaHei', sans-serif; margin: 2cm; line-height: 1.6; }
                            pre, code { font-family: 'Menlo', 'Consolas', monospace; }
                            img { max-width: 100%%; }
                        </style>
                        </head><body>%s</body></html>
                        """.formatted(html);
            }

            // openhtmltopdf requires XHTML-conformant self-closing void tags
            html = sanitizeForXhtml(html);

            String outputName = source.getName().replaceAll("(?i)\\.html?$", ".pdf");
            File outputFile = new File(source.getParent(), outputName);

            try (OutputStream os = new FileOutputStream(outputFile)) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withHtmlContent(html, null);

                ChineseFontUtils.FontResult fontResult = ChineseFontUtils.getChineseFont();
                if (fontResult != null) {
                    try {
                        File fontFile = new File(fontResult.path());
                        if (fontFile.exists()) {
                            log.info("Registering font: {} (exists: {})", fontResult.path(), fontFile.exists());
                            // PDFBox 2.x requires TrueType 'glyf' table for subsetting.
                            // CFF/OTF fonts (PingFang) must use subset=false.
                            boolean useSubset = !fontResult.isTtc();
                            builder.useFont(fontFile, "PingFang SC", null, null, useSubset);
                            builder.useFont(fontFile, "STHeiti", null, null, useSubset);
                            builder.useFont(fontFile, "PingFang", null, null, useSubset);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to register Chinese font: {}", e.getMessage());
                    }
                } else {
                    log.warn("No Chinese font found on system - PDF may not display Chinese text correctly");
                }

                builder.toStream(os);
                builder.run();
            }

            return ConversionResult.builder()
                    .success(true)
                    .fileName(outputName)
                    .resultFilePath(outputFile.getAbsolutePath())
                    .build();
        } catch (Exception e) {
            log.error("HTML to PDF conversion failed", e);
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return ConversionResult.builder()
                    .success(false).errorMessage("HTML 转 PDF 失败：" + (msg != null ? msg : e.toString())).build();
        }
    }

    private String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * openhtmltopdf uses an XML parser which requires void tags to be self-closed.
     * This converts HTML void elements to XHTML-compatible self-closing format.
     */
    private String sanitizeForXhtml(String html) {
        return html.replaceAll("(?i)<(br|hr|img|input|meta|link|param|source|track|wbr)(\\s[^>]*?)?(\\s*/)?>",
                "<$1$2/>");
    }
}
