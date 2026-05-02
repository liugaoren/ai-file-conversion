package com.file.conversion.converter;

import com.file.conversion.model.ConversionResult;

import java.io.File;
import java.util.Set;

public interface Converter {
    Set<FormatPair> supportedFormats();
    ConversionResult convert(File source, String targetFormat);
}
