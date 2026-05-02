package com.file.conversion.converter;

import com.file.conversion.model.ConversionResult;
import com.file.conversion.util.ChineseFontUtils;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.Background;
import com.itextpdf.layout.properties.Property;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.TransparentColor;
import com.itextpdf.layout.properties.Underline;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigInteger;
import java.util.*;

@Component
public class WordToPdfConverter implements Converter {

    private static final Logger log = LoggerFactory.getLogger(WordToPdfConverter.class);

    // Cache fonts by family name to avoid recreating on every run
    private static final Map<String, PdfFont> fontCache = new HashMap<>();
    private static final Border TABLE_BORDER = new SolidBorder(new DeviceRgb(180, 180, 180), 0.5f);

    @Override
    public Set<FormatPair> supportedFormats() {
        return Set.of(
                FormatPair.of("doc", "pdf"),
                FormatPair.of("docx", "pdf")
        );
    }

    @Override
    public ConversionResult convert(File source, String targetFormat) {
        String name = source.getName().toLowerCase();
        try {
            String outputName = source.getName().replaceAll("(?i)\\.docx?$", ".pdf");
            File outputFile = new File(source.getParent(), outputName);

            if (name.endsWith(".docx")) {
                convertDocx(source, outputFile);
            } else {
                convertDoc(source, outputFile);
            }

            return ConversionResult.builder()
                    .success(true)
                    .fileName(outputName)
                    .resultFilePath(outputFile.getAbsolutePath())
                    .build();
        } catch (Exception e) {
            log.error("Word to PDF failed", e);
            return ConversionResult.builder()
                    .success(false).errorMessage("Word 转 PDF 失败：" + e.getMessage()).build();
        }
    }

    private void convertDocx(File source, File outputFile) throws Exception {
        try (InputStream is = new FileInputStream(source);
             XWPFDocument doc = new XWPFDocument(is)) {

            PdfFont baseFont = loadChineseFont();

            // Read page setup
            float marginLeft = 72f, marginRight = 72f, marginTop = 72f, marginBottom = 72f;
            PageSize pageSize = PageSize.A4;

            var ctSectPr = doc.getDocument().getBody().getSectPr();
            if (ctSectPr != null) {
                var ctPgSz = ctSectPr.getPgSz();
                if (ctPgSz != null && ctPgSz.getW() != null && ctPgSz.getH() != null) {
                    float w = ((BigInteger) ctPgSz.getW()).longValue() / 20f;
                    float h = ((BigInteger) ctPgSz.getH()).longValue() / 20f;
                    if (w > 0 && h > 0) {
                        var orient = ctPgSz.getOrient();
                        boolean landscape = orient != null
                                && "landscape".equalsIgnoreCase(orient.toString());
                        pageSize = landscape ? new PageSize(h, w) : new PageSize(w, h);
                    }
                }
                var ctMar = ctSectPr.getPgMar();
                if (ctMar != null) {
                    if (ctMar.getLeft() != null) marginLeft = ((BigInteger) ctMar.getLeft()).longValue() / 20f;
                    if (ctMar.getRight() != null) marginRight = ((BigInteger) ctMar.getRight()).longValue() / 20f;
                    if (ctMar.getTop() != null) marginTop = ((BigInteger) ctMar.getTop()).longValue() / 20f;
                    if (ctMar.getBottom() != null) marginBottom = ((BigInteger) ctMar.getBottom()).longValue() / 20f;
                }
            }

            try (OutputStream os = new FileOutputStream(outputFile);
                 PdfWriter writer = new PdfWriter(os);
                 PdfDocument pdfDoc = new PdfDocument(writer);
                 Document pdf = new Document(pdfDoc, pageSize)) {

                pdf.setMargins(marginTop, marginRight, marginBottom, marginLeft);

                Map<BigInteger, Integer> listCounters = new HashMap<>();

                for (IBodyElement element : doc.getBodyElements()) {
                    if (element instanceof XWPFParagraph xwpfPara) {
                        BigInteger numId = xwpfPara.getNumID();
                        if (numId != null && !numId.equals(getPreviousNumId(doc, element))) {
                            listCounters.put(numId, 0);
                        }

                        Paragraph pdfPara = convertParagraph(xwpfPara, baseFont, listCounters, doc);
                        if (pdfPara != null && !pdfPara.isEmpty()) {
                            pdf.add(pdfPara);
                        }
                    } else if (element instanceof XWPFTable xwpfTable) {
                        Table pdfTable = convertTable(xwpfTable, baseFont);
                        if (pdfTable != null) {
                            pdf.add(pdfTable);
                        }
                    }
                }
            }
        }
    }

    private void convertDoc(File source, File outputFile) throws Exception {
        // For old .doc format, extract text and create a simple PDF
        String text;
        try (InputStream is = new FileInputStream(source);
             HWPFDocument doc = new HWPFDocument(is);
             WordExtractor extractor = new WordExtractor(doc)) {
            text = extractor.getText();
        }

        PdfFont baseFont = loadChineseFont();

        try (OutputStream os = new FileOutputStream(outputFile);
             PdfWriter writer = new PdfWriter(os);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document pdf = new Document(pdfDoc, PageSize.A4)) {

            pdf.setMargins(72, 72, 72, 72);

            for (String para : text.split("\n")) {
                para = para.trim();
                if (!para.isEmpty()) {
                    Paragraph p = new Paragraph(para);
                    if (baseFont != null) p.setProperty(Property.FONT, baseFont);
                    p.setProperty(Property.FONT_SIZE, UnitValue.createPointValue(12f));
                    pdf.add(p);
                }
            }
        }
    }

    private BigInteger getPreviousNumId(XWPFDocument doc, IBodyElement current) {
        BigInteger prev = null;
        for (IBodyElement element : doc.getBodyElements()) {
            if (element == current) break;
            if (element instanceof XWPFParagraph para) {
                BigInteger numId = para.getNumID();
                if (numId != null) prev = numId;
            }
        }
        return prev;
    }

    // ---- Paragraph conversion ----

    private Paragraph convertParagraph(XWPFParagraph xwpfPara, PdfFont baseFont,
                                        Map<BigInteger, Integer> listCounters, XWPFDocument doc) {
        Paragraph pdfPara = new Paragraph();

        // Alignment
        var alignment = xwpfPara.getAlignment();
        if (alignment != null) {
            switch (alignment) {
                case CENTER -> pdfPara.setTextAlignment(TextAlignment.CENTER);
                case RIGHT -> pdfPara.setTextAlignment(TextAlignment.RIGHT);
                case BOTH -> pdfPara.setTextAlignment(TextAlignment.JUSTIFIED);
                default -> {}
            }
        }

        // Spacing
        double spBefore = xwpfPara.getSpacingBefore();
        if (spBefore > 0) pdfPara.setMarginTop((float) (spBefore / 20));
        double spAfter = xwpfPara.getSpacingAfter();
        if (spAfter > 0) pdfPara.setMarginBottom((float) (spAfter / 20));

        // Line spacing — handle according to spacing rule
        setLineSpacing(xwpfPara, pdfPara, baseFont, doc);

        // Indentation
        int firstLine = xwpfPara.getIndentationFirstLine();
        if (firstLine != 0) pdfPara.setFirstLineIndent(firstLine / 20f);
        int indentLeft = xwpfPara.getIndentationLeft();
        if (indentLeft != 0) pdfPara.setMarginLeft(indentLeft / 20f);

        // List prefix
        BigInteger numId = xwpfPara.getNumID();
        if (numId != null) {
            int count = listCounters.merge(numId, 1, Integer::sum);
            String prefix = getListItemPrefix(xwpfPara, count);
            Text prefixText = new Text(prefix);
            if (baseFont != null) prefixText.setProperty(Property.FONT, baseFont);
            prefixText.setProperty(Property.FONT_SIZE, UnitValue.createPointValue(12f));
            pdfPara.add(prefixText);
            int ilvl = xwpfPara.getNumIlvl() != null ? xwpfPara.getNumIlvl().intValue() : 0;
            float existingIndent = indentLeft / 20f;
            pdfPara.setMarginLeft(existingIndent + (ilvl + 1) * 22f);
        }

        // Process runs
        for (XWPFRun run : xwpfPara.getRuns()) {
            // Check for embedded images in this run
            List<XWPFPicture> pictures = run.getEmbeddedPictures();
            if (!pictures.isEmpty()) {
                for (XWPFPicture picture : pictures) {
                    try {
                        Image pdfImage = convertPicture(picture);
                        if (pdfImage != null) {
                            pdfPara.add(pdfImage);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to embed image", e);
                    }
                }
            }

            String text = run.getText(0);
            if (text != null && !text.isEmpty()) {
                Text pdfText = new Text(text);
                applyTextFormatting(run, pdfText, baseFont, xwpfPara, doc);
                pdfPara.add(pdfText);
            }
        }

        // Fallback: paragraph has text but no runs
        if (xwpfPara.getRuns().isEmpty() && !xwpfPara.getText().isBlank()) {
            Text fallbackText = new Text(xwpfPara.getText());
            if (baseFont != null) fallbackText.setProperty(Property.FONT, baseFont);
            fallbackText.setProperty(Property.FONT_SIZE, UnitValue.createPointValue(12f));
            pdfPara.add(fallbackText);
        }

        if (pdfPara.isEmpty()) return null;
        return pdfPara;
    }

    private Image convertPicture(XWPFPicture picture) throws Exception {
        XWPFPictureData pictureData = picture.getPictureData();
        byte[] data = pictureData.getData();
        if (data == null || data.length == 0) return null;

        // Determine image type
        String ext = pictureData.suggestFileExtension();
        if (ext == null) ext = "png";

        com.itextpdf.layout.element.Image pdfImage =
                new Image(ImageDataFactory.create(data));

        // Scale image to fit page width (respect Word document width)
        float maxWidth = 480f; // reasonable default
        if (pdfImage.getImageWidth() > maxWidth) {
            float ratio = maxWidth / pdfImage.getImageWidth();
            pdfImage.scale(ratio, ratio);
        }

        return pdfImage;
    }

    private void setLineSpacing(XWPFParagraph para, Paragraph pdfPara,
                                 PdfFont baseFont, XWPFDocument doc) {
        var pPr = para.getCTP().getPPr();
        if (pPr == null) return;

        var spacing = pPr.getSpacing();
        if (spacing == null) return;

        Object lineObj = spacing.getLine();
        if (lineObj == null) return;
        long lineLong = lineObj instanceof BigInteger bi ? bi.longValue() : Long.parseLong(lineObj.toString());
        if (lineLong == 0) return;

        float lineValue = (float) lineLong;

        // Determine the line spacing rule
        var lineRule = spacing.getLineRule();
        String rule = lineRule != null ? lineRule.toString() : "auto";

        float leading;
        switch (rule) {
            case "exact":
                // Exact line spacing in twips
                leading = lineValue / 20f;
                pdfPara.setFixedLeading(leading);
                break;
            case "atLeast":
                // Minimum line spacing in twips — iText has no min-leading, use fixed
                leading = lineValue / 20f;
                pdfPara.setFixedLeading(leading);
                break;
            default: // auto — lineValue is a multiplier * 240
                float lineMultiple = lineValue / 240f;
                int fontSize = resolveFontSize(para, doc);
                float baseLeading = fontSize > 0 ? (fontSize / 2f) * 1.2f : 14.4f;
                pdfPara.setFixedLeading(baseLeading * lineMultiple);
                break;
        }
    }

    // ---- Text formatting ----

    private void applyTextFormatting(XWPFRun run, Text pdfText, PdfFont baseFont,
                                      XWPFParagraph para, XWPFDocument doc) {
        // Font — prefer cached font based on the run's font family
        String fontFamily = run.getFontFamily();
        if (fontFamily == null || fontFamily.isEmpty()) fontFamily = resolveFontFamily(para, doc);
        if (fontFamily != null && !fontFamily.isEmpty()) {
            PdfFont cachedFont = getOrCreateFont(fontFamily);
            if (cachedFont != null) {
                pdfText.setProperty(Property.FONT, cachedFont);
            } else if (baseFont != null) {
                pdfText.setProperty(Property.FONT, baseFont);
            }
        } else if (baseFont != null) {
            pdfText.setProperty(Property.FONT, baseFont);
        }

        // Font size
        int fontSize = run.getFontSize();
        if (fontSize == -1) fontSize = resolveFontSize(para, doc);
        if (fontSize != -1) {
            pdfText.setProperty(Property.FONT_SIZE, UnitValue.createPointValue(fontSize / 2.0f));
        }

        // Bold
        var runRPr = run.getCTR().getRPr();
        boolean runHasBold = runRPr != null && runRPr.sizeOfBArray() > 0;
        if (runHasBold) {
            if (run.isBold()) pdfText.setProperty(Property.BOLD_SIMULATION, true);
        } else {
            if (resolveBold(para, doc)) pdfText.setProperty(Property.BOLD_SIMULATION, true);
        }

        // Italic
        boolean runHasItalic = runRPr != null && runRPr.sizeOfIArray() > 0;
        if (runHasItalic) {
            if (run.isItalic()) pdfText.setProperty(Property.ITALIC_SIMULATION, true);
        } else {
            if (resolveItalic(para, doc)) pdfText.setProperty(Property.ITALIC_SIMULATION, true);
        }

        // Underline
        if (run.getUnderline() != UnderlinePatterns.NONE) {
            List<Underline> underlines = Collections.singletonList(
                    new Underline(null, 0f, 0.5f, 0f, 0f, 0));
            pdfText.setProperty(Property.UNDERLINE, underlines);
        }

        // Text highlight (background color)
        if (runRPr != null && runRPr.sizeOfHighlightArray() > 0) {
            var highlight = runRPr.getHighlightArray(0);
            var colorVal = highlight.getVal();
            if (colorVal != null) {
                DeviceRgb bgColor = mapHighlightColor(colorVal.toString());
                if (bgColor != null) {
                    pdfText.setProperty(Property.BACKGROUND, new Background(bgColor));
                }
            }
        }

        // Font color
        String color = run.getColor();
        if (color == null || color.isEmpty()) color = resolveColor(para, doc);
        if (color != null && !color.isEmpty()) {
            try {
                int rgb = Integer.parseInt(color, 16);
                pdfText.setProperty(Property.FONT_COLOR, new TransparentColor(new DeviceRgb(
                        (rgb >> 16) & 0xFF,
                        (rgb >> 8) & 0xFF,
                        rgb & 0xFF)));
            } catch (Exception ignored) {
            }
        }
    }

    private DeviceRgb mapHighlightColor(String colorName) {
        return switch (colorName.toLowerCase()) {
            case "yellow" -> new DeviceRgb(255, 255, 0);
            case "green" -> new DeviceRgb(0, 255, 0);
            case "cyan" -> new DeviceRgb(0, 255, 255);
            case "magenta" -> new DeviceRgb(255, 0, 255);
            case "blue" -> new DeviceRgb(0, 0, 255);
            case "red" -> new DeviceRgb(255, 0, 0);
            case "darkblue" -> new DeviceRgb(0, 0, 128);
            case "darkcyan" -> new DeviceRgb(0, 128, 128);
            case "darkgreen" -> new DeviceRgb(0, 128, 0);
            case "darkmagenta" -> new DeviceRgb(128, 0, 128);
            case "darkred" -> new DeviceRgb(128, 0, 0);
            case "darkyellow" -> new DeviceRgb(128, 128, 0);
            case "darkgray", "darkgrey" -> new DeviceRgb(64, 64, 64);
            case "lightgray", "lightgrey" -> new DeviceRgb(192, 192, 192);
            case "black" -> new DeviceRgb(0, 0, 0);
            case "white" -> new DeviceRgb(255, 255, 255);
            default -> null;
        };
    }

    private PdfFont getOrCreateFont(String fontFamily) {
        if (fontFamily == null || fontFamily.isEmpty()) return null;
        String key = fontFamily.toLowerCase();
        PdfFont cached = fontCache.get(key);
        if (cached != null) return cached;

        try {
            cached = PdfFontFactory.createFont(fontFamily, "Identity-H",
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            fontCache.put(key, cached);
            return cached;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Style inheritance helpers ----

    private int resolveFontSize(XWPFParagraph para, XWPFDocument doc) {
        var pPr = para.getCTP().getPPr();
        if (pPr != null) {
            var rPr = pPr.getRPr();
            if (rPr != null && rPr.sizeOfSzArray() > 0 && rPr.getSzArray(0).getVal() != null) {
                return ((Number) rPr.getSzArray(0).getVal()).intValue();
            }
        }
        Integer val = getStyleSz(para.getStyle(), doc);
        if (val != null) return val;
        val = getStyleSz("Normal", doc);
        return val != null ? val : -1;
    }

    private Integer getStyleSz(String styleId, XWPFDocument doc) {
        if (styleId == null || styleId.isEmpty()) return null;
        try {
            var style = doc.getStyles().getStyle(styleId);
            if (style == null) return null;
            var ctStyle = style.getCTStyle();
            if (ctStyle == null) return null;
            var rPr = ctStyle.getRPr();
            if (rPr != null && rPr.sizeOfSzArray() > 0 && rPr.getSzArray(0).getVal() != null) {
                return ((Number) rPr.getSzArray(0).getVal()).intValue();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String resolveFontFamily(XWPFParagraph para, XWPFDocument doc) {
        var pPr = para.getCTP().getPPr();
        if (pPr != null) {
            var rPr = pPr.getRPr();
            if (rPr != null && rPr.sizeOfRFontsArray() > 0) {
                var fonts = rPr.getRFontsArray(0);
                String f = fonts.getAscii();
                if (f == null || f.isEmpty()) f = fonts.getHAnsi();
                if (f == null || f.isEmpty()) f = fonts.getEastAsia();
                if (f != null && !f.isEmpty()) return f;
            }
        }
        String val = getStyleFontFamily(para.getStyle(), doc);
        if (val != null) return val;
        return getStyleFontFamily("Normal", doc);
    }

    private String getStyleFontFamily(String styleId, XWPFDocument doc) {
        if (styleId == null || styleId.isEmpty()) return null;
        try {
            var style = doc.getStyles().getStyle(styleId);
            if (style == null) return null;
            var ctStyle = style.getCTStyle();
            if (ctStyle == null) return null;
            var rPr = ctStyle.getRPr();
            if (rPr != null && rPr.sizeOfRFontsArray() > 0) {
                var fonts = rPr.getRFontsArray(0);
                String f = fonts.getAscii();
                if (f == null || f.isEmpty()) f = fonts.getHAnsi();
                if (f == null || f.isEmpty()) f = fonts.getEastAsia();
                if (f != null && !f.isEmpty()) return f;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean resolveBold(XWPFParagraph para, XWPFDocument doc) {
        var pPr = para.getCTP().getPPr();
        if (pPr != null) {
            var rPr = pPr.getRPr();
            if (rPr != null && rPr.sizeOfBArray() > 0) return isOnOffTrue(rPr.getBArray(0));
        }
        Boolean val = getStyleBold(para.getStyle(), doc);
        if (val != null) return val;
        val = getStyleBold("Normal", doc);
        return val != null && val;
    }

    private boolean resolveItalic(XWPFParagraph para, XWPFDocument doc) {
        var pPr = para.getCTP().getPPr();
        if (pPr != null) {
            var rPr = pPr.getRPr();
            if (rPr != null && rPr.sizeOfIArray() > 0) return isOnOffTrue(rPr.getIArray(0));
        }
        Boolean val = getStyleItalic(para.getStyle(), doc);
        if (val != null) return val;
        val = getStyleItalic("Normal", doc);
        return val != null && val;
    }

    private Boolean getStyleBold(String styleId, XWPFDocument doc) {
        if (styleId == null || styleId.isEmpty()) return null;
        try {
            var style = doc.getStyles().getStyle(styleId);
            if (style == null) return null;
            var ctStyle = style.getCTStyle();
            if (ctStyle == null) return null;
            var rPr = ctStyle.getRPr();
            if (rPr != null && rPr.sizeOfBArray() > 0) return isOnOffTrue(rPr.getBArray(0));
        } catch (Exception ignored) {}
        return null;
    }

    private Boolean getStyleItalic(String styleId, XWPFDocument doc) {
        if (styleId == null || styleId.isEmpty()) return null;
        try {
            var style = doc.getStyles().getStyle(styleId);
            if (style == null) return null;
            var ctStyle = style.getCTStyle();
            if (ctStyle == null) return null;
            var rPr = ctStyle.getRPr();
            if (rPr != null && rPr.sizeOfIArray() > 0) return isOnOffTrue(rPr.getIArray(0));
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isOnOffTrue(Object onOff) {
        if (onOff == null) return false;
        try {
            var getVal = onOff.getClass().getMethod("getVal");
            Object val = getVal.invoke(onOff);
            if (val == null) return true;
            String s = val.toString();
            return "true".equalsIgnoreCase(s) || "on".equalsIgnoreCase(s) || "1".equals(s);
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveColor(XWPFParagraph para, XWPFDocument doc) {
        var pPr = para.getCTP().getPPr();
        if (pPr != null) {
            var rPr = pPr.getRPr();
            if (rPr != null && rPr.sizeOfColorArray() > 0) {
                var color = rPr.getColorArray(0);
                Object val = color.getVal();
                if (val != null && !val.toString().isEmpty()) return val.toString();
            }
        }
        String val = getStyleColor(para.getStyle(), doc);
        if (val != null) return val;
        return getStyleColor("Normal", doc);
    }

    private String getStyleColor(String styleId, XWPFDocument doc) {
        if (styleId == null || styleId.isEmpty()) return null;
        try {
            var style = doc.getStyles().getStyle(styleId);
            if (style == null) return null;
            var ctStyle = style.getCTStyle();
            if (ctStyle == null) return null;
            var rPr = ctStyle.getRPr();
            if (rPr != null && rPr.sizeOfColorArray() > 0) {
                var color = rPr.getColorArray(0);
                Object val = color.getVal();
                if (val != null && !val.toString().isEmpty()) return val.toString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ---- List prefix ----

    private String getListItemPrefix(XWPFParagraph para, int count) {
        try {
            String numFmt = para.getNumFmt();
            if (numFmt == null) return "• ";
            return switch (numFmt) {
                case "bullet", "bullets", "bulet" -> "• ";
                case "decimal" -> count + ". ";
                case "lowerLetter" -> ((char) ('a' + (count - 1) % 26)) + ". ";
                case "upperLetter" -> ((char) ('A' + (count - 1) % 26)) + ". ";
                case "lowerRoman" -> toRoman(count).toLowerCase() + ". ";
                case "upperRoman" -> toRoman(count) + ". ";
                default -> count + ". ";
            };
        } catch (Exception e) {
            return "• ";
        }
    }

    private static String toRoman(int n) {
        if (n <= 0 || n > 3999) return String.valueOf(n);
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) {
                sb.append(symbols[i]);
                n -= values[i];
            }
        }
        return sb.toString();
    }

    // ---- Table conversion ----

    private Table convertTable(XWPFTable xwpfTable, PdfFont baseFont) {
        var tblGrid = xwpfTable.getCTTbl().getTblGrid();
        int cols = 0;
        if (tblGrid != null) {
            cols = tblGrid.sizeOfGridColArray();
        }
        if (cols == 0) {
            for (XWPFTableRow row : xwpfTable.getRows()) {
                cols = Math.max(cols, row.getTableCells().size());
            }
        }
        if (cols == 0) return null;

        // Column widths as percentages
        float[] colWidths = new float[cols];
        if (tblGrid != null && tblGrid.sizeOfGridColArray() == cols) {
            long totalW = 0;
            long[] twips = new long[cols];
            for (int i = 0; i < cols; i++) {
                Object wObj = tblGrid.getGridColArray(i).getW();
                twips[i] = wObj != null ? ((BigInteger) wObj).longValue() : 1440;
                totalW += twips[i];
            }
            if (totalW > 0) {
                for (int i = 0; i < cols; i++) {
                    colWidths[i] = (float) twips[i] / totalW * 100f;
                }
            } else {
                Arrays.fill(colWidths, 100f / cols);
            }
        } else {
            Arrays.fill(colWidths, 100f / cols);
        }

        Table pdfTable = new Table(UnitValue.createPercentArray(colWidths));
        pdfTable.setWidth(UnitValue.createPercentValue(100));

        // Track rowspans across rows
        // key = colIndex, value = remaining rowspan (not yet consumed)
        Map<Integer, RowSpanState> activeRowSpans = new HashMap<>();

        for (XWPFTableRow row : xwpfTable.getRows()) {
            int colIdx = 0;

            for (XWPFTableCell cell : row.getTableCells()) {
                // Skip columns covered by active rowspan from previous rows
                while (activeRowSpans.containsKey(colIdx)) {
                    RowSpanState state = activeRowSpans.get(colIdx);
                    state.remainingRows--;
                    if (state.remainingRows <= 0) {
                        activeRowSpans.remove(colIdx);
                    }
                    colIdx++;
                }

                if (colIdx >= cols) break;

                // Parse cell properties
                int gridSpan = 1;
                int vMerge = 0; // 0=none, 1=restart, 2=continue
                String cellColor = null;

                try {
                    var tcPr = cell.getCTTc().getTcPr();
                    if (tcPr != null) {
                        if (tcPr.isSetGridSpan()) {
                            gridSpan = tcPr.getGridSpan().getVal().intValue();
                        }
                        if (tcPr.getVMerge() != null) {
                            var vMergeVal = tcPr.getVMerge().getVal();
                            if (vMergeVal == null) {
                                vMerge = 1; // restart
                            } else if ("continue".equals(vMergeVal.toString())) {
                                vMerge = 2; // continue
                            } else {
                                vMerge = 1;
                            }
                        }
                        if (tcPr.getShd() != null) {
                            Object fillVal = tcPr.getShd().getFill();
                            String fill = fillVal != null ? fillVal.toString() : null;
                            if (fill != null && !fill.isEmpty() && !"auto".equals(fill)) {
                                cellColor = fill;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }

                // Skip continuation cells (they were handled in a previous row)
                if (vMerge == 2) {
                    colIdx += gridSpan;
                    continue;
                }

                // Determine rowspan (estimate from vMerge chain)
                int rowSpan = 1;
                if (vMerge == 1) {
                    rowSpan = countVMergeRowSpan(xwpfTable, row, colIdx);
                    if (rowSpan > 1) {
                        // Register rowspan states for covered columns
                        for (int i = 0; i < gridSpan && (colIdx + i) < cols; i++) {
                            activeRowSpans.put(colIdx + i, new RowSpanState(rowSpan));
                        }
                    }
                }

                Cell pdfCell;
                if (rowSpan > 1 || gridSpan > 1) {
                    pdfCell = new Cell(rowSpan, gridSpan);
                } else {
                    pdfCell = new Cell(1, 1);
                }

                // Cell padding
                pdfCell.setPadding(4f);

                // Cell border
                pdfCell.setBorder(TABLE_BORDER);

                // Cell background color
                if (cellColor != null) {
                    try {
                        int rgb = Integer.parseInt(cellColor, 16);
                        pdfCell.setBackgroundColor(new DeviceRgb(
                                (rgb >> 16) & 0xFF,
                                (rgb >> 8) & 0xFF,
                                rgb & 0xFF));
                    } catch (Exception ignored) {
                    }
                }

                // Cell vertical alignment
                try {
                    var tcPr = cell.getCTTc().getTcPr();
                    if (tcPr != null && tcPr.getVAlign() != null) {
                        var vAlignVal = tcPr.getVAlign().getVal();
                        if (vAlignVal != null) {
                            switch (vAlignVal.toString().toLowerCase()) {
                                case "center" -> pdfCell.setVerticalAlignment(VerticalAlignment.MIDDLE);
                                case "bottom" -> pdfCell.setVerticalAlignment(VerticalAlignment.BOTTOM);
                                default -> pdfCell.setVerticalAlignment(VerticalAlignment.TOP);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }

                // Cell text — process paragraphs inside the cell
                String cellText = cell.getText();
                if (cellText != null && !cellText.isBlank()) {
                    Paragraph cellPara = new Paragraph(cellText);
                    if (baseFont != null) cellPara.setProperty(Property.FONT, baseFont);
                    cellPara.setProperty(Property.FONT_SIZE, UnitValue.createPointValue(10f));

                    // Check for images in cell paragraphs
                    for (XWPFParagraph cellP : cell.getParagraphs()) {
                        for (XWPFRun run : cellP.getRuns()) {
                            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                                try {
                                    Image img = convertPicture(picture);
                                    if (img != null) cellPara.add(img);
                                } catch (Exception e) {
                                    log.warn("Failed to embed image in table cell", e);
                                }
                            }
                        }
                    }

                    pdfCell.add(cellPara);
                } else {
                    pdfCell.add(new Paragraph(""));
                }

                pdfTable.addCell(pdfCell);
                colIdx += gridSpan;
            }

            // Fill remaining columns
            while (colIdx < cols) {
                Cell emptyCell = new Cell().add(new Paragraph(""));
                emptyCell.setBorder(TABLE_BORDER);
                emptyCell.setPadding(4f);
                pdfTable.addCell(emptyCell);
                colIdx++;
            }
        }

        return pdfTable;
    }

    private int countVMergeRowSpan(XWPFTable table, XWPFTableRow startRow, int colIdx) {
        int span = 1;
        boolean foundStart = false;
        for (XWPFTableRow row : table.getRows()) {
            if (row == startRow) {
                foundStart = true;
                continue;
            }
            if (!foundStart) continue;

            int cellCol = 0;
            for (XWPFTableCell cell : row.getTableCells()) {
                int gridSpan = 1;
                try {
                    var tcPr = cell.getCTTc().getTcPr();
                    if (tcPr != null && tcPr.isSetGridSpan()) {
                        gridSpan = tcPr.getGridSpan().getVal().intValue();
                    }
                } catch (Exception ignored) {}

                if (cellCol <= colIdx && colIdx < cellCol + gridSpan) {
                    try {
                        var tcPr = cell.getCTTc().getTcPr();
                        if (tcPr != null && tcPr.getVMerge() != null) {
                            var val = tcPr.getVMerge().getVal();
                            if (val != null && "continue".equals(val.toString())) {
                                span++;
                            } else {
                                return span;
                            }
                        } else {
                            return span;
                        }
                    } catch (Exception e) {
                        return span;
                    }
                }
                cellCol += gridSpan;
            }
            return span; // cell not found in this row
        }
        return span;
    }

    // ---- Font loading ----

    private PdfFont loadChineseFont() {
        try {
            ChineseFontUtils.FontResult fontResult = ChineseFontUtils.getChineseFont();
            if (fontResult == null) return null;

            if (fontResult.isTtc()) {
                return PdfFontFactory.createTtcFont(
                        fontResult.path(),
                        fontResult.ttcIndex(),
                        "Identity-H",
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED,
                        true);
            } else {
                return PdfFontFactory.createFont(
                        fontResult.path(),
                        "Identity-H",
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            }
        } catch (Exception e) {
            log.warn("无法加载中文字体，使用默认字体: {}", e.getMessage());
        }
        return null;
    }

    // ---- Helper record ----

    private static class RowSpanState {
        int remainingRows;
        RowSpanState(int remainingRows) {
            this.remainingRows = remainingRows;
        }
    }
}
