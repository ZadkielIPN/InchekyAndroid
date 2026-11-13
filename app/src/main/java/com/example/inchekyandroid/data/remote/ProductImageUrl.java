package com.example.inchekyandroid.data.remote;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ProductImageUrl {
    private ProductImageUrl() {
    }

    public static String fromApiBase(String apiBaseUrl, String image) {
        if (image == null || image.trim().isEmpty()) {
            return null;
        }

        String value = image.trim();
        try {
            if (value.regionMatches(true, 0, "http://", 0, 7)
                    || value.regionMatches(true, 0, "https://", 0, 8)) {
                URI imageUri = new URI(value);
                if (imageUri.getHost() == null) {
                    throw new IllegalArgumentException("La URL absoluta de imagen no tiene host.");
                }
                return imageUri.toASCIIString();
            }

            URI base = new URI(apiBaseUrl);
            String encodedName = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
            URI path = URI.create("/uploads/productos/" + encodedName);
            return base.resolve(path).toASCIIString();
        } catch (URISyntaxException | UnsupportedEncodingException error) {
            throw new IllegalArgumentException("La URL de imagen del producto no es válida.", error);
        }
    }
}
