package com.file.conversion.converter;

import com.file.conversion.model.ConversionResult;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class ExcelToCsvConverter implements Converter {

    @Override
    public Set<FormatPair> supportedFormats() {
        return Set.of(
                FormatPair.of("xls", "csv"),
                FormatPair.of("xlsx", "csv")
        );
    }

    @Override
    public ConversionResult convert(File source, String targetFormat) {
        try (Workbook workbook = WorkbookFactory.create(source)) {
            StringBuilder csv = new StringBuilder();
            DataFormatter formatter = new DataFormatter();

            boolean multiSheet = workbook.getNumberOfSheets() > 1;

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);

                for (Row row : sheet) {
                    int colStart = 0;
                    // Prepend sheet name as first column for multi-sheet CSVs
                    if (multiSheet) {
                        csv.append(escapeCsv(sheet.getSheetName())).append(",");
                        colStart = 1;
                    }
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c);
                        String value = formatter.formatCellValue(cell);
                        csv.append(escapeCsv(value));
                        if (c < row.getLastCellNum() - 1) csv.append(",");
                    }
                    csv.append("\n");
                }
            }

            String outputName = source.getName().replaceAll("(?i)\\.xlsx?$", ".csv");
            File outputFile = new File(source.getParent(), outputName);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                writer.write(csv.toString());
            }

            return ConversionResult.builder()
                    .success(true)
                    .fileName(outputName)
                    .resultFilePath(outputFile.getAbsolutePath())
                    .previewContent(csv.length() <= 5000 ? csv.toString() : null)
                    .build();
        } catch (Exception e) {
            return ConversionResult.builder()
                    .success(false).errorMessage("Excel 转 CSV 错误：" + e.getMessage()).build();
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
