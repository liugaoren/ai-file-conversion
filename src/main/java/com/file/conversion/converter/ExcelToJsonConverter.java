package com.file.conversion.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.file.conversion.model.ConversionResult;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.*;

@Component
public class ExcelToJsonConverter implements Converter {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public Set<FormatPair> supportedFormats() {
        return Set.of(
                FormatPair.of("xls", "json"),
                FormatPair.of("xlsx", "json")
        );
    }

    @Override
    public ConversionResult convert(File source, String targetFormat) {
        try (Workbook workbook = WorkbookFactory.create(source)) {
            DataFormatter formatter = new DataFormatter();
            Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                List<Map<String, String>> rows = new ArrayList<>();

                Row headerRow = sheet.getRow(0);
                if (headerRow == null) continue;

                List<String> headers = new ArrayList<>();
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    headers.add(formatter.formatCellValue(headerRow.getCell(c)));
                }

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    Map<String, String> rowMap = new LinkedHashMap<>();
                    for (int c = 0; c < headers.size(); c++) {
                        String value = c < row.getLastCellNum()
                                ? formatter.formatCellValue(row.getCell(c))
                                : "";
                        rowMap.put(headers.get(c), value);
                    }
                    rows.add(rowMap);
                }

                result.put(sheet.getSheetName(), rows);
            }

            String json = objectMapper.writeValueAsString(result);
            String outputName = source.getName().replaceAll("(?i)\\.xlsx?$", ".json");
            File outputFile = new File(source.getParent(), outputName);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile))) {
                writer.write(json);
            }

            return ConversionResult.builder()
                    .success(true)
                    .fileName(outputName)
                    .resultFilePath(outputFile.getAbsolutePath())
                    .previewContent(json.length() <= 5000 ? json : json.substring(0, 5000) + "...")
                    .build();
        } catch (Exception e) {
            return ConversionResult.builder()
                    .success(false).errorMessage("Excel 转 JSON 错误：" + e.getMessage()).build();
        }
    }
}
