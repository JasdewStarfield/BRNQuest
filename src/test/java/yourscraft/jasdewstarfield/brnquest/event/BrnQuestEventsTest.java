package yourscraft.jasdewstarfield.brnquest.event;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.QuestBookView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrnQuestEventsTest {
    @Test
    void failingObserverDoesNotInterruptLaterObservers() {
        AtomicInteger delivered = new AtomicInteger();
        QuestBookReloadedEvent event = new QuestBookReloadedEvent(null,
                new QuestBookView(ResourceLocation.parse("test:book"), 1, "Book", "REV",
                        List.of(), List.of(), List.of(), Map.of()));

        try (EventSubscription first = BrnQuestEvents.subscribe(QuestBookReloadedEvent.class,
                ignored -> { throw new IllegalStateException("listener failure"); });
             EventSubscription second = BrnQuestEvents.subscribe(QuestBookReloadedEvent.class,
                     ignored -> delivered.incrementAndGet())) {
            BrnQuestEvents.post(event);
        }

        assertEquals(1, delivered.get());
    }

    @Test
    void closedSubscriptionReceivesNoMoreEvents() {
        AtomicInteger delivered = new AtomicInteger();
        EventSubscription subscription = BrnQuestEvents.subscribe(QuestBookReloadedEvent.class,
                ignored -> delivered.incrementAndGet());
        subscription.close();

        BrnQuestEvents.post(new QuestBookReloadedEvent(null,
                new QuestBookView(ResourceLocation.parse("test:book"), 1, "Book", "REV",
                        List.of(), List.of(), List.of(), Map.of())));

        assertEquals(0, delivered.get());
    }
}
