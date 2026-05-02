package com.file.conversion.converter;

import com.file.conversion.model.ConversionResult;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.Arrays;
import java.util.Set;

@Component
public class MdToHtmlConverter implements Converter {

    @Override
    public Set<FormatPair> supportedFormats() {
        return Set.of(FormatPair.of("md", "html"));
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
            // Preserve soft line breaks within paragraphs
            options.set(HtmlRenderer.SOFT_BREAK, "<br>\n");

            Parser parser = Parser.builder(options).build();
            HtmlRenderer renderer = HtmlRenderer.builder(options).build();
            Node document = parser.parse(markdown);
            String html = renderer.render(document);

            String fullHtml = """
                    <!DOCTYPE html>
                    <html><head><meta charset="UTF-8">
                    <title>%s</title>
                    <style>
                        * { box-sizing: border-box; }
                        body {
                            font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', sans-serif;
                            max-width: 800px; margin: 0 auto; padding: 20px;
                            line-height: 1.8; color: #333; font-size: 15px;
                        }
                        h1, h2, h3, h4, h5, h6 { margin-top: 1.5em; margin-bottom: 0.5em; font-weight: 600; line-height: 1.4; color: #1a1a1a; }
                        h1 { font-size: 1.8em; border-bottom: 2px solid #eee; padding-bottom: 0.3em; }
                        h2 { font-size: 1.5em; border-bottom: 1px solid #eee; padding-bottom: 0.2em; }
                        h3 { font-size: 1.25em; }
                        p { margin: 0.5em 0; }
                        pre {
                            background: #f6f8fa; padding: 16px; border-radius: 6px;
                            overflow-x: auto; line-height: 1.45; font-size: 13px;
                            border: 1px solid #e1e4e8;
                        }
                        code {
                            background: #f0f2f4; padding: 2px 6px; border-radius: 3px;
                            font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
                            font-size: 0.9em; color: #d63384;
                        }
                        pre code { background: none; padding: 0; color: inherit; font-size: inherit; }
                        table {
                            border-collapse: collapse; width: 100%%; margin: 0.8em 0;
                            display: block; overflow-x: auto;
                        }
                        th, td {
                            border: 1px solid #dfe2e5; padding: 8px 12px;
                            text-align: left; vertical-align: top;
                        }
                        th { background: #f6f8fa; font-weight: 600; }
                        tr:nth-child(even) { background: #fafbfc; }
                        tr:hover { background: #f0f2f4; }
                        img { max-width: 100%%; height: auto; }
                        blockquote {
                            border-left: 4px solid #dfe2e5; padding: 0 1em; margin: 0.5em 0;
                            color: #6a737d; background: #f8f9fa;
                        }
                        blockquote p { padding: 0.3em 0; }
                        ul, ol { padding-left: 2em; margin: 0.5em 0; }
                        li { margin: 0.3em 0; }
                        hr { border: none; border-top: 2px solid #eee; margin: 2em 0; }
                        a { color: #0366d6; text-decoration: none; }
                        a:hover { text-decoration: underline; }
                    </style>
                    </head><body>%s</body></html>
                    """.formatted(source.getName(), html);

            String outputName = source.getName().replaceAll("(?i)\\.md$", ".html");
            File outputFile = new File(source.getParent(), outputName);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile))) {
                writer.write(fullHtml);
            }

            return ConversionResult.builder()
                    .success(true)
                    .fileName(outputName)
                    .resultFilePath(outputFile.getAbsolutePath())
                    .previewContent(html.length() <= 3000 ? html : html.substring(0, 3000) + "...")
                    .build();
        } catch (Exception e) {
            return ConversionResult.builder()
                    .success(false).errorMessage("Markdown 转 HTML 失败：" + e.getMessage()).build();
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
}
