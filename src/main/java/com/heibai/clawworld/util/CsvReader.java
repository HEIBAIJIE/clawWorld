package com.heibai.clawworld.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
public class CsvReader {

    public <T> List<T> readCsv(InputStream inputStream, Function<CSVRecord, T> mapper) throws IOException {
        List<T> results = new ArrayList<>();

        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim())) {

            for (CSVRecord record : csvParser) {
                try {
                    T item = mapper.apply(record);
                    if (item != null) {
                        results.add(item);
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing CSV record at line " + record.getRecordNumber() + ": " + e.getMessage());
                }
            }
        }

        return results;
    }

    public String getString(CSVRecord record, String column) {
        return record.get(column);
    }

    public int getInt(CSVRecord record, String column) {
        String value = record.get(column);
        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    public double getDouble(CSVRecord record, String column) {
        String value = record.get(column);
        return value.isEmpty() ? 0.0 : Double.parseDouble(value);
    }

    public boolean getBoolean(CSVRecord record, String column) {
        String value = record.get(column);
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    public Integer getIntOrNull(CSVRecord record, String column) {
        String value = record.get(column);
        return value.isEmpty() ? null : Integer.parseInt(value);
    }

    public String getStringOrNull(CSVRecord record, String column) {
        String value = record.get(column);
        return value.isEmpty() ? null : value;
    }
}
