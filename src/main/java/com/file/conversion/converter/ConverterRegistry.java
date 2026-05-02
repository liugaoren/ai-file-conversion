package com.file.conversion.converter;

import com.file.conversion.model.ConversionResult;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ConverterRegistry {

    private final List<Converter> converters;
    private final Map<FormatPair, Converter> mapping = new HashMap<>();

    public ConverterRegistry(List<Converter> converters) {
        this.converters = converters;
    }

    @PostConstruct
    public void init() {
        for (Converter converter : converters) {
            for (FormatPair pair : converter.supportedFormats()) {
                mapping.put(pair, converter);
            }
        }
    }

    public Optional<Converter> findConverter(String sourceFormat, String targetFormat) {
        return Optional.ofNullable(mapping.get(FormatPair.of(sourceFormat, targetFormat)));
    }

    public Set<FormatPair> getAllSupportedFormats() {
        return mapping.keySet();
    }
}
