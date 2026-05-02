package com.file.conversion.converter;

import com.file.conversion.model.ConversionResult;
import com.file.conversion.util.ChineseFontUtils;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.Arrays;
import java.util.Set;

@Component
public class MarkdownToPdfConverter implements Converter {

    private static final Logger log = LoggerFactory.getLogger(MarkdownToPdfConverter.class);

    @Override
    public Set<FormatPair> supportedFormats() {
        return Set.of(FormatPair.of("md", "pdf"));
    }

    @Override
    public ConversionResult convert(File source, String targetFormat) {
        try {
            String markdown = readFile(source);

            MutableDataSet options = new MutableDataSet();
            options.set(Parser.EXTENSIONS, Arrays.asList(
                    TablesExtension.create(),
                    StrikethroughExtension.create()
            ));

            Parser parser = Parser.builder(options).build();
            HtmlRenderer renderer = HtmlRenderer.builder(options).build();
            Node document = parser.parse(markdown);
            String html = renderer.render(document);

            // Check for Chinese font availability
            ChineseFontUtils.FontResult fontResult = ChineseFontUtils.getChineseFont();

            String fontFamily = "'PingFang SC', 'STHeiti', 'Microsoft YaHei', 'Helvetica', 'Arial', sans-serif";
            String fullHtml = """
                    <!DOCTYPE html>
                    <html xmlns="http://www.w3.org/1999/xhtml"><head>
                    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
                    <style>
                        body { font-family: %s; margin: 2cm; font-size: 12pt; line-height: 1.8; color: #333; }
                        h1, h2, h3, h4 { margin-top: 0.8em; margin-bottom: 0.3em; font-weight: 600; }
                        h1 { font-size: 18pt; border-bottom: 1px solid #ddd; padding-bottom: 4px; }
                        h2 { font-size: 16pt; border-bottom: 1px solid #eee; padding-bottom: 3px; }
                        h3 { font-size: 14pt; }
                        p { margin: 0.3em 0; }
                        code, pre {
                            font-family: 'Menlo', 'Consolas', 'Courier New', monospace;
                            font-size: 10pt;
                        }
                        code { background: #f0f2f4; padding: 1px 4px; }
                        pre { background: #f6f8fa; padding: 12px; border-radius: 4px; border: 1px solid #e1e4e8; line-height: 1.4; }
                        pre code { background: none; padding: 0; }
                        table { border-collapse: collapse; width: 100%%; margin: 0.5em 0; }
                        th, td { border: 1px solid #bbb; padding: 6px 10px; text-align: left; }
                        th { background: #f0f0f0; font-weight: 600; }
                        blockquote { border-left: 4px solid #ddd; margin: 0.5em 0; padding-left: 12px; color: #666; }
                        img { max-width: 100%%; }
                        ul, ol { padding-left: 2em; margin: 0.3em 0; }
                        li { margin: 0.2em 0; }
                        hr { border: none; border-top: 1px solid #ddd; margin: 1em 0; }
                    </style>
                    </head><body>%s</body></html>
                    """.formatted(fontFamily, sanitizeForXhtml(html));

            String outputName = source.getName().replaceAll("(?i)\\.md$", ".pdf");
            File outputFile = new File(source.getParent(), outputName);

            try (OutputStream os = new FileOutputStream(outputFile)) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withHtmlContent(fullHtml, null);

                // Register Chinese font for PDF rendering
                if (fontResult != null) {
                    try {
                        File fontFile = new File(fontResult.path());
                        if (fontFile.exists()) {
                            log.info("Registering font: {} (exists: {})", fontResult.path(), fontFile.exists());
                            // PDFBox 2.x requires TrueType 'glyf' table for subsetting.
                            // CFF/OTF fonts (PingFang) must use subset=false.
                            // Standalone TTF (Arial Unicode, AppleGothic) work with subset=true.
                            boolean useSubset = !fontResult.isTtc();
                            builder.useFont(fontFile, "PingFang SC", null, null, useSubset);
                            builder.useFont(fontFile, "STHeiti", null, null, useSubset);
                            builder.useFont(fontFile, "PingFang", null, null, useSubset);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to register Chinese font for PDF: {}", e.getMessage());
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
            log.error("Markdown to PDF conversion failed", e);
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return ConversionResult.builder()
                    .success(false).errorMessage("Markdown 转 PDF 失败：" + (msg != null ? msg : e.toString())).build();
        }
    }

    /**
     * openhtmltopdf uses an XML parser which requires void tags to be self-closed.
     * This converts HTML void elements to XHTML-compatible self-closing format.
     */
    private String sanitizeForXhtml(String html) {
        // Void elements that must be self-closed in XHTML
        // Regex: <tag(attrs)> → <tag(attrs)/>
        return html.replaceAll("<(br|hr|img|input|meta|link|param|source|track|wbr)(\\s[^>]*?)?(\\s*/)?>",
                "<$1$2/>");
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
}
