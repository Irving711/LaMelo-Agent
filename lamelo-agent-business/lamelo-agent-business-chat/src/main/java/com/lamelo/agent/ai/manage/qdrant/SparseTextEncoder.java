package com.lamelo.agent.ai.manage.qdrant;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

@Component
public class SparseTextEncoder {

    private static final Pattern TOKEN = Pattern.compile("[\\u3400-\\u9fff]+|[A-Za-z0-9._-]+");

    public Map<String, Object> encode(Map<String, Double> weightedFields) {
        Map<Long, Double> weights = new LinkedHashMap<>();
        weightedFields.forEach((text, boost) -> {
            if (text == null || text.isBlank() || boost == null || boost <= 0) {
                return;
            }
            Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
            while (matcher.find()) {
                String value = matcher.group();
                if (value.charAt(0) >= '\u3400' && value.charAt(0) <= '\u9fff') {
                    int[] chars = value.codePoints().toArray();
                    if (chars.length == 1) {
                        weights.merge(termId(value), boost, Double::sum);
                    }
                    for (int i = 0; i < chars.length - 1; i++) {
                        weights.merge(termId(new String(chars, i, 2)), boost, Double::sum);
                    }
                }
                else {
                    weights.merge(termId(value), boost, Double::sum);
                }
            }
        });
        List<Long> indices = new ArrayList<>(weights.keySet());
        List<Double> values = indices.stream().map(weights::get).toList();
        return Map.of("indices", indices, "values", values);
    }

    public Map<String, Object> encode(String text) {
        return encode(Map.of(text == null ? "" : text, 1D));
    }

    private long termId(String term) {
        CRC32 crc32 = new CRC32();
        crc32.update(term.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }
}
