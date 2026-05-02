package com.file.conversion.converter;

public record FormatPair(String sourceFormat, String targetFormat) {

    public static FormatPair of(String source, String target) {
        return new FormatPair(source.toLowerCase(), target.toLowerCase());
    }
}
