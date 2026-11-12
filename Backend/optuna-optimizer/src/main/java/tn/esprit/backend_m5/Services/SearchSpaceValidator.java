package tn.esprit.backend_m5.Services;

import java.util.List;
import java.util.Map;


public final class SearchSpaceValidator {

    private SearchSpaceValidator() {
    }

    public static void validate(Map<String, Object> searchSpace) {
        if (searchSpace == null || searchSpace.isEmpty()) {
            throw new IllegalArgumentException("searchSpace ne peut pas etre vide");
        }
        for (Map.Entry<String, Object> entry : searchSpace.entrySet()) {
            String name = entry.getKey();
            if (!(entry.getValue() instanceof Map)) {
                throw new IllegalArgumentException("searchSpace." + name + " doit etre un objet");
            }
            Map<?, ?> spec = (Map<?, ?>) entry.getValue();
            Object type = spec.get("type");

            if ("float".equals(type) || "int".equals(type)) {
                Object low = spec.get("low");
                Object high = spec.get("high");
                if (!(low instanceof Number) || !(high instanceof Number)) {
                    throw new IllegalArgumentException("searchSpace." + name + " : low et high numeriques requis");
                }
                if (((Number) low).doubleValue() >= ((Number) high).doubleValue()) {
                    throw new IllegalArgumentException("searchSpace." + name + " : low doit etre inferieur a high");
                }
            } else if ("categorical".equals(type)) {
                Object choices = spec.get("choices");
                if (!(choices instanceof List) || ((List<?>) choices).isEmpty()) {
                    throw new IllegalArgumentException("searchSpace." + name + " : choices (liste non vide) requis");
                }
            } else {
                throw new IllegalArgumentException(
                        "searchSpace." + name + " : type doit valoir float, int ou categorical");
            }
        }
    }
}