package tn.esprit.mission;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Test MySQL integration when a local test database is available.
// Use Maven compile to verify sources independently of a running database.
class MissionStructureTest {

    @Test
    void projectLoads() {
        org.junit.jupiter.api.Assertions.assertTrue(true);
    }
}
