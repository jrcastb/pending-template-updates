package com.example.pendingupdates.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pendingupdates.domain.BindingSnapshot.Cause;
import com.example.pendingupdates.domain.BindingUpdate.Changed;
import com.example.pendingupdates.domain.BindingUpdate.Ignored;
import com.example.pendingupdates.domain.EngagementBinding.ConflictingRevision;
import com.example.pendingupdates.domain.EngagementBinding.TemplateMismatch;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EngagementBindingTest {

    private static final int NONE = 0;
    private static final TemplateId AUDIT = new TemplateId("audit");

    private final BindingSnapshot created = snapshot(1, Cause.CREATED, 2, NONE);
    private final BindingSnapshot applied = snapshot(2, Cause.APPLIED, 3, NONE);
    private final BindingSnapshot declined = snapshot(3, Cause.DECLINED, 3, 5);

    @Test
    void firstSnapshotCreatesTheBinding() {
        EngagementBinding binding = EngagementBinding.from(created);

        assertThat(binding.applied()).isEqualTo(new Version(2));
        assertThat(binding.lastDeclined()).isEmpty();
        assertThat(binding.revision()).isEqualTo(1L);
    }

    @Test
    void newerRevisionReplacesTheBinding() {
        EngagementBinding binding = EngagementBinding.from(created);

        assertThat(binding.applyIfNewer(applied)).isEqualTo(new Changed(EngagementBinding.from(applied)));
    }

    @Test
    void identicalRedeliveryIsIgnoredAsDuplicate() {
        EngagementBinding binding = EngagementBinding.from(applied);

        assertThat(binding.applyIfNewer(applied)).isEqualTo(new Ignored(Ignored.Reason.DUPLICATE));
    }

    @Test
    void olderRevisionIsIgnoredAsStale() {
        EngagementBinding binding = EngagementBinding.from(applied);

        assertThat(binding.applyIfNewer(created)).isEqualTo(new Ignored(Ignored.Reason.STALE));
    }

    @Test
    void sameRevisionWithDifferentContentIsAProducerBug() {
        EngagementBinding binding = EngagementBinding.from(applied);
        BindingSnapshot conflicting = snapshot(2, Cause.APPLIED, 4, NONE);

        assertThatThrownBy(() -> binding.applyIfNewer(conflicting))
                .isInstanceOf(ConflictingRevision.class);
    }

    @Test
    void lateApplyAfterANewerDeclineKeepsTheDecline() {
        // given the decline (rev 3) arrived before the apply (rev 2)
        EngagementBinding binding = EngagementBinding.from(declined);

        // when the older apply arrives late
        BindingUpdate update = binding.applyIfNewer(applied);

        // then it is ignored and the decline still stands
        assertThat(update).isEqualTo(new Ignored(Ignored.Reason.STALE));
        assertThat(binding.lastDeclined()).contains(new Version(5));
    }

    @Test
    void anyDeliveryOrderEndsInTheSameState() {
        EngagementBinding expected = EngagementBinding.from(declined);
        List<List<BindingSnapshot>> allOrders = List.of(
                List.of(created, applied, declined), List.of(created, declined, applied),
                List.of(applied, created, declined), List.of(applied, declined, created),
                List.of(declined, created, applied), List.of(declined, applied, created));

        for (List<BindingSnapshot> order : allOrders) {
            assertThat(replay(order)).as("order %s", order).isEqualTo(expected);
        }
    }

    @Test
    void declineAtOrBelowAppliedIsNormalizedToNoDecline() {
        assertThat(snapshot(4, Cause.APPLIED, 5, 3).lastDeclined()).isEmpty();
        assertThat(snapshot(4, Cause.APPLIED, 5, 5).lastDeclined()).isEmpty();
    }

    @Test
    void snapshotForAnotherTemplateIsRejected() {
        EngagementBinding binding = EngagementBinding.from(created);
        BindingSnapshot otherTemplate = new BindingSnapshot(created.firmId(), created.engagementId(),
                new TemplateId("review"), new Version(3), Optional.empty(), 2L, Cause.APPLIED);

        assertThatThrownBy(() -> binding.applyIfNewer(otherTemplate))
                .isInstanceOf(TemplateMismatch.class);
    }

    private static EngagementBinding replay(List<BindingSnapshot> order) {
        EngagementBinding binding = EngagementBinding.from(order.get(0));
        for (BindingSnapshot next : order.subList(1, order.size())) {
            binding = switch (binding.applyIfNewer(next)) {
                case Changed changed -> changed.binding();
                case Ignored ignored -> binding;
            };
        }
        return binding;
    }

    private static BindingSnapshot snapshot(long revision, Cause cause, int applied, int declined) {
        Optional<Version> lastDeclined =
                declined == NONE ? Optional.empty() : Optional.of(new Version(declined));
        return new BindingSnapshot(new FirmId("firm-a"), new EngagementId("eng-1"), AUDIT,
                new Version(applied), lastDeclined, revision, cause);
    }
}
