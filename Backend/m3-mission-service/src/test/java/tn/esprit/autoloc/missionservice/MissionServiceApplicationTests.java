package tn.esprit.autoloc.missionservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:mission-context;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false",
    "spring.cloud.config.enabled=false",
    "m1.enabled=false",
    "m6.enabled=false",
    "orchestrator.enabled=false",
    "events.enabled=false"
})
class MissionServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
