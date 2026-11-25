package tn.esprit.mission;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tn.esprit.mission.entity.*;
import tn.esprit.mission.enums.ExecutionStatus;
import tn.esprit.mission.repository.MissionEventOutboxRepository;
import tn.esprit.mission.service.MissionEventPublisher;

class MissionEventPublisherTest {

  MissionEventOutboxRepository outbox;
  RabbitTemplate rabbit;
  MissionEventPublisher publisher;
  MissionEventOutbox event;

  @BeforeEach
  void setup() {
    outbox = mock(MissionEventOutboxRepository.class);
    rabbit = mock(RabbitTemplate.class);
    publisher = new MissionEventPublisher(
      outbox,
      new ObjectMapper(),
      rabbit,
      mock(PlatformTransactionManager.class),
      true,
      "mission.events",
      "mission.lifecycle"
    );
    event = new MissionEventOutbox();
    event.id = "stable-id";
    event.executionId = 7L;
    event.payload = "{}";
    event.nextAttemptAt = Instant.now();
    when(
      outbox.findTop20ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
        any()
      )
    ).thenReturn(List.of(event));
    when(outbox.findById(event.id)).thenReturn(Optional.of(event));
  }

  @Test
  void confirmedMessagePreservesIdAndMarksPublication() {
    doAnswer(call -> {
      Message message = call.getArgument(2);
      CorrelationData correlation = call.getArgument(3);
      assertEquals("stable-id", message.getMessageProperties().getMessageId());
      assertEquals(
        MessageDeliveryMode.PERSISTENT,
        message.getMessageProperties().getDeliveryMode()
      );
      correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
      return null;
    })
      .when(rabbit)
      .send(
        eq("mission.events"),
        eq("mission.lifecycle"),
        any(Message.class),
        any(CorrelationData.class)
      );
    publisher.publishPending();
    assertNotNull(event.publishedAt);
    assertNull(event.lastError);
  }

  @Test
  void unroutedMessageIsNotMarkedPublishedAndIsRetried() {
    doAnswer(call -> {
      CorrelationData correlation = call.getArgument(3);
      correlation.setReturned(
        new ReturnedMessage(
          call.getArgument(2),
          312,
          "NO_ROUTE",
          "mission.events",
          "mission.lifecycle"
        )
      );
      correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
      return null;
    })
      .when(rabbit)
      .send(
        anyString(),
        anyString(),
        any(Message.class),
        any(CorrelationData.class)
      );
    publisher.publishPending();
    assertNull(event.publishedAt);
    assertEquals(1, event.attempts);
    assertTrue(event.nextAttemptAt.isAfter(Instant.now()));
  }

  @Test
  void brokerFailurePreservesEventForLaterDelivery() {
    doThrow(new IllegalStateException("offline"))
      .when(rabbit)
      .send(
        anyString(),
        anyString(),
        any(Message.class),
        any(CorrelationData.class)
      );
    publisher.publishPending();
    assertNull(event.publishedAt);
    assertEquals(1, event.attempts);
    assertNotNull(event.lastError);
  }

  @Test
  void eventContainsMetricsButNeverTheMissionResult() throws Exception {
    var execution = new MissionExecution();
    execution.setId(7L);
    execution.setMissionId(3L);
    execution.setStatus(ExecutionStatus.COMPLETED);
    execution.setMetricsSource("SIMULATED");
    execution.setAttemptsUsed(2);
    execution.setCandidateResult("private-content");
    publisher.enqueue(execution);
    var capture = org.mockito.ArgumentCaptor.forClass(MissionEventOutbox.class);
    verify(outbox).save(capture.capture());
    var body = new ObjectMapper().readTree(capture.getValue().payload);
    assertEquals("MISSION_COMPLETED", body.get("eventType").asText());
    assertTrue(body.get("tokensUsed").isNull());
    assertFalse(capture.getValue().payload.contains("private-content"));
    assertEquals(capture.getValue().id, body.get("eventId").asText());
  }
}
