package com.example.pendingupdates.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pendingupdates.domain.BindingSnapshot.Cause;
import com.example.pendingupdates.domain.BindingUpdate.Changed;
import com.example.pendingupdates.domain.BindingUpdate.Created;
import com.example.pendingupdates.domain.BindingUpdate.Ignored;
import com.example.pendingupdates.domain.EngagementBinding.ConflictingRevision;
import com.example.pendingupdates.domain.EngagementBinding.RejectedSnapshot;
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
        BindingUpdate update = EngagementBinding.applyIfNewer(Optional.empty(), created);

        assertThat(update).isEqualTo(new Created(EngagementBinding.from(created)));
    }

    @Test
    void newerRevisionReplacesTheBinding() {
        BindingUpdate update = EngagementBinding.applyIfNewer(stored(created), applied);

        assertThat(update).isEqualTo(new Changed(EngagementBinding.from(applied)));
    }

    @Test
    void identicalRedeliveryIsIgnoredAsDuplicate() {
        assertThat(EngagementBinding.applyIfNewer(stored(applied), applied))
                .isEqualTo(new Ignored(Ignored.Reason.DUPLICATE));
    }

    @Test
    void olderRevisionIsIgnoredAsStale() {
        assertThat(EngagementBinding.applyIfNewer(stored(applied), created))
                .isEqualTo(new Ignored(Ignored.Reason.STALE));
    }

    @Test
    void sameRevisionWithDifferentContentIsRejected() {
        BindingSnapshot conflicting = snapshot(2, Cause.APPLIED, 4, NONE);

        assertThatThrownBy(() -> EngagementBinding.applyIfNewer(stored(applied), conflicting))
                .isInstanceOf(ConflictingRevision.class)
                .isInstanceOf(RejectedSnapshot.class);
    }

    @Test
    void lateApplyAfterANewerDeclineKeepsTheDecline() {
        // given the decline (rev 3) arrived before the apply (rev 2)
        Optional<EngagementBinding> current = stored(declined);

        // when the older apply arrives late
        BindingUpdate update = EngagementBinding.applyIfNewer(current, applied);

        // then it is ignored and the decline still stands
        assertThat(update).isEqualTo(new Ignored(Ignored.Reason.STALE));
        assertThat(current.orElseThrow().lastDeclined()).contains(new Version(5));
    }

    @Test
    void anyDeliveryOrderEndsInTheSameState() {
        EngagementBinding expected = EngagementBinding.from(declined);
        List<List<BindingSnapshot>> allOrders = List.of(
                List.of(created, applied, declined), List.of(created, declined, applied),
                List.of(applied, created, declined), List.of(applied, declined, created),
                List.of(declined, created, applied), List.of(declined, applied, created));

        for (List<BindingSnapshot> order : allOrders) {
            assertThat(replay(order)).as("order %s", order).contains(expected);
        }
    }

    @Test
    void declineAtOrBelowAppliedIsNormalizedToNoDecline() {
        assertThat(snapshot(4, Cause.APPLIED, 5, 3).lastDeclined()).isEmpty();
        assertThat(snapshot(4, Cause.APPLIED, 5, 5).lastDeclined()).isEmpty();
    }

    @Test
    void snapshotForAnotherTemplateIsRejected() {
        BindingSnapshot otherTemplate = new BindingSnapshot(created.firmId(), created.engagementId(),
                new TemplateId("review"), new Version(3), Optional.empty(), 2L, Cause.APPLIED);

        assertThatThrownBy(() -> EngagementBinding.applyIfNewer(stored(created), otherTemplate))
                .isInstanceOf(TemplateMismatch.class)
                .isInstanceOf(RejectedSnapshot.class);
    }

    private static Optional<EngagementBinding> replay(List<BindingSnapshot> order) {
        Optional<EngagementBinding> current = Optional.empty();
        for (BindingSnapshot next : order) {
            current = switch (EngagementBinding.applyIfNewer(current, next)) {
                case Created created -> Optional.of(created.binding());
                case Changed changed -> Optional.of(changed.binding());
                case Ignored ignored -> current;
            };
        }
        return current;
    }

    private static Optional<EngagementBinding> stored(BindingSnapshot snapshot) {
        return Optional.of(EngagementBinding.from(snapshot));
    }

    private static BindingSnapshot snapshot(long revision, Cause cause, int applied, int declined) {
        Optional<Version> lastDeclined =
                declined == NONE ? Optional.empty() : Optional.of(new Version(declined));
        return new BindingSnapshot(new FirmId("firm-a"), new EngagementId("eng-1"), AUDIT,
                new Version(applied), lastDeclined, revision, cause);
    }
}
