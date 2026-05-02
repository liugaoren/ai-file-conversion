package com.file.conversion.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class ChineseFontUtils {

    private static final Logger log = LoggerFactory.getLogger(ChineseFontUtils.class);

    // Ordered by PDFBox/openhtmltopdf compatibility:
    //  1. Standalone TTF (not from TTC) with glyf table — most compatible
    //  2. TrueType-based TTC (glyf table in individual fonts)
    //  3. CFF/OTF fonts — only compatible with iText
    private static final List<FontCandidate> FONT_CANDIDATES = List.of(
            // macOS — standalone TrueType TTF files (most compatible with PDFBox)
            new FontCandidate("/System/Library/Fonts/Supplemental/Arial Unicode.ttf", 0, false),
            new FontCandidate("/System/Library/Fonts/Supplemental/AppleGothic.ttf", 0, false),
            new FontCandidate("/System/Library/Fonts/Supplemental/AppleMyungjo.ttf", 0, false),
            // macOS — TrueType-based TTC fonts
            new FontCandidate("/System/Library/Fonts/STHeiti Light.ttc", 0, true),
            new FontCandidate("/System/Library/Fonts/Supplemental/Songti.ttc", 0, true),
            new FontCandidate("/System/Library/Fonts/STSong.ttf", 0, false),
            // macOS — CFF-based (only compatible with iText, not PDFBox)
            new FontCandidate("/System/Library/Fonts/PingFang.ttc", 0, true),
            new FontCandidate("/System/Library/Fonts/AppleSDGothicNeo.ttc", 0, true),
            // macOS user-installed fonts
            new FontCandidate("/Library/Fonts/MicrosoftYaHei.ttf", 0, false),
            new FontCandidate("/Library/Fonts/NotoSansCJK-Regular.ttc", 0, true),
            // Linux — WenQuanYi (TrueType-based)
            new FontCandidate("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", 0, true),
            new FontCandidate("/usr/share/fonts/truetype/wqy/wqy-microhei.ttc", 0, true),
            // Linux Noto Sans CJK (CFF-based)
            new FontCandidate("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", 0, true),
            new FontCandidate("/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc", 0, true),
            new FontCandidate("/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc", 0, true),
            new FontCandidate("/usr/share/fonts/opentype/wqy/wqy-zenhei.ttc", 0, true),
            // Windows (TrueType-based)
            new FontCandidate("C:/Windows/Fonts/simsun.ttc", 0, true),
            new FontCandidate("C:/Windows/Fonts/msyh.ttc", 0, true),
            new FontCandidate("C:/Windows/Fonts/msyhbd.ttc", 0, true),
            new FontCandidate("C:/Windows/Fonts/simhei.ttf", 0, false),
            new FontCandidate("C:/Windows/Fonts/yahei.ttf", 0, false)
    );

    private static FontResult foundFont = null;

    public static FontResult getChineseFont() {
        if (foundFont != null) return foundFont;

        for (FontCandidate candidate : FONT_CANDIDATES) {
            File fontFile = new File(candidate.path());
            if (fontFile.exists()) {
                log.info("Found Chinese font: {} (TTC index {}, isTTC: {})",
                        candidate.path(), candidate.ttcIndex(), candidate.isTtc());
                foundFont = new FontResult(candidate.path(), candidate.ttcIndex(), candidate.isTtc());
                return foundFont;
            }
        }

        log.warn("No Chinese font found on this system.");
        return null;
    }

    public record FontCandidate(String path, int ttcIndex, boolean isTtc) {}
    public record FontResult(String path, int ttcIndex, boolean isTtc) {}
}
