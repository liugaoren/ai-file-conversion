package com.file.conversion.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.file.conversion.model.ConversionResult;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

@Component
public class JsonToCsvConverter implements Converter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Set<FormatPair> supportedFormats() {
        return Set.of(FormatPair.of("json", "csv"));
    }

    @Override
    public ConversionResult convert(File source, String targetFormat) {
        try {
            JsonNode root = objectMapper.readTree(source);

            // Support both array of objects and single object
            List<JsonNode> items = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(items::add);
            } else {
                items.add(root);
            }

            if (items.isEmpty()) {
                return ConversionResult.builder()
                        .success(false).errorMessage("JSON 数据为空").build();
            }

            // Collect all unique keys as headers
            Set<String> headerSet = new LinkedHashSet<>();
            for (JsonNode item : items) {
                if (item.isObject()) {
                    item.fieldNames().forEachRemaining(headerSet::add);
                }
            }

            List<String> headers = new ArrayList<>(headerSet);
            StringBuilder csv = new StringBuilder();

            // Header row
            for (int i = 0; i < headers.size(); i++) {
                csv.append(escapeCsv(headers.get(i)));
                if (i < headers.size() - 1) csv.append(",");
            }
            csv.append("\n");

            // Data rows
            for (JsonNode item : items) {
                for (int i = 0; i < headers.size(); i++) {
                    JsonNode value = item.get(headers.get(i));
                    csv.append(escapeCsv(value != null ? value.asText() : ""));
                    if (i < headers.size() - 1) csv.append(",");
                }
                csv.append("\n");
            }

            String outputName = source.getName().replaceAll("(?i)\\.json$", ".csv");
            File outputFile = new File(source.getParent(), outputName);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile))) {
                writer.write(csv.toString());
            }

            return ConversionResult.builder()
                    .success(true)
                    .fileName(outputName)
                    .resultFilePath(outputFile.getAbsolutePath())
                    .previewContent(csv.length() <= 3000 ? csv.toString() : csv.substring(0, 3000) + "...")
                    .build();
        } catch (Exception e) {
            return ConversionResult.builder()
                    .success(false).errorMessage("JSON 转 CSV 失败：" + e.getMessage()).build();
        }
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
