package com.file.conversion.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.file.conversion.model.ConversionResult;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

@Component
public class CsvToJsonConverter implements Converter {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public Set<FormatPair> supportedFormats() {
        return Set.of(FormatPair.of("csv", "json"));
    }

    @Override
    public ConversionResult convert(File source, String targetFormat) {
        try {
            List<String> lines = readLines(source);
            if (lines.isEmpty()) {
                return ConversionResult.builder()
                        .success(false).errorMessage("CSV 文件为空").build();
            }

            String[] headers = parseCsvLine(lines.get(0));
            List<Map<String, String>> rows = new ArrayList<>();

            for (int i = 1; i < lines.size(); i++) {
                String[] values = parseCsvLine(lines.get(i));
                Map<String, String> row = new LinkedHashMap<>();
                for (int j = 0; j < headers.length; j++) {
                    row.put(headers[j], j < values.length ? values[j] : "");
                }
                rows.add(row);
            }

            String json = objectMapper.writeValueAsString(rows);
            String outputName = source.getName().replaceAll("(?i)\\.csv$", ".json");
            File outputFile = new File(source.getParent(), outputName);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile))) {
                writer.write(json);
            }

            return ConversionResult.builder()
                    .success(true)
                    .fileName(outputName)
                    .resultFilePath(outputFile.getAbsolutePath())
                    .previewContent(json.length() <= 3000 ? json : json.substring(0, 3000) + "...")
                    .build();
        } catch (Exception e) {
            return ConversionResult.builder()
                    .success(false).errorMessage("CSV 转 JSON 失败：" + e.getMessage()).build();
        }
    }

    private List<String> readLines(File file) throws IOException {
        // Read entire file content to handle multi-line quoted CSV fields
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }

        // Parse into logical rows, respecting quoted fields with newlines
        List<String> rows = new ArrayList<>();
        StringBuilder currentRow = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            currentRow.append(c);
            if (c == '"') {
                if (i + 1 < sb.length() && sb.charAt(i + 1) == '"') {
                    currentRow.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == '\n' && !inQuotes) {
                String row = currentRow.toString().trim();
                if (!row.isEmpty()) {
                    rows.add(row);
                }
                currentRow = new StringBuilder();
            }
        }
        // Last row without trailing newline
        String lastRow = currentRow.toString().trim();
        if (!lastRow.isEmpty()) {
            rows.add(lastRow);
        }

        return rows;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString().trim());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString().trim());
        return fields.toArray(new String[0]);
    }
}
