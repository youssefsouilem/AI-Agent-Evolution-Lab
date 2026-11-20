package tn.esprit.agentservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.esprit.agentservice.entity.AgentConfiguration;
import tn.esprit.agentservice.exception.InvalidConfigurationException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TunableParameterCatalogTest {

    private TunableParameterCatalog catalog;
    private AgentConfiguration configuration;

    @BeforeEach
    void setUp() {
        catalog = new TunableParameterCatalog();
        configuration = AgentConfiguration.builder()
                .temperature(0.7)
                .maxTurns(3)
                .timeoutSeconds(30)
                .build();
    }

    @Test
    void appliesValidValues() {
        catalog.applyParams(configuration, Map.of(
                "temperature", 1.2,
                "maxTokens", 2048,
                "maxTurns", 5,
                "timeoutSeconds", 60,
                "style", "detaille"
        ));

        assertEquals(1.2, configuration.getTemperature());
        assertEquals(2048, configuration.getMaxTokens());
        assertEquals(5, configuration.getMaxTurns());
        assertEquals(60, configuration.getTimeoutSeconds());
        assertEquals("detaille", configuration.getStyle());
    }

    @Test
    void rejectsUnknownParameter() {
        assertThrows(InvalidConfigurationException.class,
                () -> catalog.applyParams(configuration, Map.of("model", "gpt-4o-mini")));
    }

    @Test
    void rejectsOutOfRangeValue() {
        assertThrows(InvalidConfigurationException.class,
                () -> catalog.applyParams(configuration, Map.of("temperature", 2.1)));
    }

    @Test
    void rejectsFractionalIntegerParameter() {
        assertThrows(InvalidConfigurationException.class,
                () -> catalog.applyParams(configuration, Map.of("maxTokens", 1.5)));
    }

    @Test
    void rejectsInvalidChoice() {
        assertThrows(InvalidConfigurationException.class,
                () -> catalog.applyParams(configuration, Map.of("style", "verbose")));
    }

    @Test
    void reportsDefaultsForNullableSettingsAndOmitsCategoricalBounds() {
        var parameters = catalog.describe(configuration);

        assertEquals(1024, parameters.get(1).current());
        assertEquals("concise", parameters.get(4).current());
        assertNull(parameters.get(4).min());
        assertNull(parameters.get(4).max());
    }

    @Test
    void rejectsNullParameterValue() {
        var params = new java.util.HashMap<String, Object>();
        params.put("style", null);

        assertThrows(InvalidConfigurationException.class, () -> catalog.applyParams(configuration, params));
    }
}
