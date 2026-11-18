package tn.esprit.agentservice.service;

import org.springframework.stereotype.Component;
import tn.esprit.agentservice.dto.TunableParameterDto;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.exception.InvalidConfigurationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class TunableParameterCatalog {

    private static final List<String> STYLE_CHOICES = List.of("concise", "detaille");

    public List<TunableParameterDto> describe(AgentConfiguration configuration) {
        if (configuration == null) {
            throw new InvalidConfigurationException("La configuration de la version est absente.");
        }
        return List.of(
                new TunableParameterDto(
                        "temperature", "float", 0.0, 2.0, null,
                        configuration.getTemperature(), "Créativité"
                ),
                new TunableParameterDto(
                        "maxTokens", "int", 256, 4096, null,
                        configuration.getMaxTokens() == null ? 1024 : configuration.getMaxTokens(),
                        "Longueur maximale de la réponse"
                ),
                new TunableParameterDto(
                        "maxTurns", "int", 1, 10, null,
                        configuration.getMaxTurns(), "Nombre maximal d'appels d'outils"
                ),
                new TunableParameterDto(
                        "timeoutSeconds", "int", 5, 120, null,
                        configuration.getTimeoutSeconds(), "Délai maximal d'exécution (secondes)"
                ),
                new TunableParameterDto(
                        "style", "categorical", null, null, STYLE_CHOICES,
                        configuration.getStyle() == null ? "concise" : configuration.getStyle(),
                        "Style de réponse"
                )
        );
    }

    public void applyParams(AgentConfiguration configuration, Map<String, Object> params) {
        if (configuration == null) {
            throw new InvalidConfigurationException("La configuration à modifier est absente.");
        }
        if (params == null || params.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> parameter : params.entrySet()) {
            String name = parameter.getKey();
            Object value = parameter.getValue();
            if (value == null) {
                throw invalid(name, "la valeur ne peut pas être nulle");
            }
            switch (name) {
                case "temperature" -> configuration.setTemperature(
                        decimalValue(name, value, 0.0, 2.0).doubleValue()
                );
                case "maxTokens" -> configuration.setMaxTokens(
                        integerValue(name, value, 256, 4096)
                );
                case "maxTurns" -> configuration.setMaxTurns(
                        integerValue(name, value, 1, 10)
                );
                case "timeoutSeconds" -> configuration.setTimeoutSeconds(
                        integerValue(name, value, 5, 120)
                );
                case "style" -> {
                    if (!(value instanceof String style)) {
                        throw invalid(name, "la valeur doit être une chaîne");
                    }
                    if (!STYLE_CHOICES.contains(style)) {
                        throw invalid(name, "la valeur doit être concise ou detaille");
                    }
                    configuration.setStyle(style);
                }
                default -> throw invalid(name, "paramètre inconnu");
            }
        }
    }

    private BigDecimal decimalValue(String name, Object value, double min, double max) {
        BigDecimal decimal = numberValue(name, value);
        if (decimal.compareTo(BigDecimal.valueOf(min)) < 0 || decimal.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw invalid(name, "la valeur doit être comprise entre " + min + " et " + max);
        }
        return decimal;
    }

    private int integerValue(String name, Object value, int min, int max) {
        BigDecimal decimal = numberValue(name, value);
        final int integer;
        try {
            integer = decimal.intValueExact();
        } catch (ArithmeticException exception) {
            throw invalid(name, "la valeur doit être un nombre entier");
        }
        if (integer < min || integer > max) {
            throw invalid(name, "la valeur doit être comprise entre " + min + " et " + max);
        }
        return integer;
    }

    private BigDecimal numberValue(String name, Object value) {
        if (!(value instanceof Number number)) {
            throw invalid(name, "la valeur doit être un nombre");
        }
        try {
            if (number instanceof Double doubleValue && !Double.isFinite(doubleValue)
                    || number instanceof Float floatValue && !Float.isFinite(floatValue)) {
                throw invalid(name, "la valeur doit être un nombre fini");
            }
            return new BigDecimal(number.toString());
        } catch (NumberFormatException exception) {
            throw invalid(name, "la valeur doit être un nombre valide");
        }
    }

    private InvalidConfigurationException invalid(String name, String reason) {
        return new InvalidConfigurationException("Paramètre '" + name + "' invalide : " + reason + ".");
    }
}
