package com.file.conversion.converter;

import com.file.conversion.model.ConversionResult;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Set;

@Component
public class PngToJpgConverter implements Converter {

    @Override
    public Set<FormatPair> supportedFormats() {
        return Set.of(FormatPair.of("png", "jpg"), FormatPair.of("png", "jpeg"));
    }

    @Override
    public ConversionResult convert(File source, String targetFormat) {
        try {
            BufferedImage image = ImageIO.read(source);
            if (image == null) {
                return ConversionResult.builder()
                        .success(false).errorMessage("无法读取 PNG 图片").build();
            }

            // Create a new RGB image (PNG may be ARGB, JPG doesn't support alpha)
            BufferedImage rgb = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.createGraphics().drawImage(image, 0, 0, java.awt.Color.WHITE, null);

            String outputName = source.getName().replaceAll("(?i)\\.png$", ".jpg");
            File outputFile = new File(source.getParent(), outputName);

            boolean written = ImageIO.write(rgb, "jpg", outputFile);
            if (!written) {
                return ConversionResult.builder()
                        .success(false).errorMessage("写入 JPG 文件失败").build();
            }

            return ConversionResult.builder()
                    .success(true)
                    .fileName(outputName)
                    .resultFilePath(outputFile.getAbsolutePath())
                    .build();
        } catch (IOException e) {
            return ConversionResult.builder()
                    .success(false).errorMessage("PNG 转 JPG 转换错误：" + e.getMessage()).build();
        }
    }
}
