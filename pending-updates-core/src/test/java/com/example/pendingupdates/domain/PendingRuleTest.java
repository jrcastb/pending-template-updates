package com.example.pendingupdates.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pendingupdates.domain.PendingStatus.Checking;
import com.example.pendingupdates.domain.PendingStatus.Checking.Reason;
import com.example.pendingupdates.domain.PendingStatus.UpToDate;
import com.example.pendingupdates.domain.PendingStatus.UpdateAvailable;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PendingRuleTest {

    private static final int NONE = 0;

    // applied, lastDeclined (NONE = absent), latest (NONE = unknown), expected
    static Stream<Arguments> rule() {
        return Stream.of(
                row("latest unknown", 2, NONE, NONE, new Checking(Reason.LATEST_UNKNOWN)),
                row("applied ahead of latest", 5, NONE, 3, new Checking(Reason.LATEST_BEHIND)),
                row("declined ahead of latest", 2, 6, 5, new Checking(Reason.LATEST_BEHIND)),
                row("applied equals latest", 5, NONE, 5, new UpToDate()),
                row("two versions behind", 2, NONE, 5, offer(2, 5, false)),
                row("skips a declined version", 2, 3, 5, offer(2, 5, true)),
                row("latest was declined", 2, 5, 5, new UpToDate()),
                row("stale decline below applied", 3, 2, 5, offer(3, 5, false)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rule")
    void pendingRule(String scenario, EngagementBinding binding, Optional<Version> latest,
            PendingStatus expected) {
        assertThat(PendingRule.evaluate(Optional.of(binding), latest)).isEqualTo(expected);
    }

    @Test
    void engagementWithoutBindingIsChecking() {
        assertThat(PendingRule.evaluate(Optional.empty(), Optional.of(new Version(5))))
                .isEqualTo(new Checking(Reason.NO_BINDING));
    }

    @Test
    void declinedUpdateIsOfferedAgainWhenANewerVersionIsPublished() {
        // given an engagement on v2 that declined v5
        EngagementBinding binding = binding(2, 5);

        // when v6 is published
        PendingStatus status = PendingRule.evaluate(Optional.of(binding), version(6));

        // then the cumulative 2 -> 6 update is offered, flagged as including a declined version
        assertThat(status).isEqualTo(offer(2, 6, true));
    }

    private static Arguments row(String scenario, int applied, int declined, int latest,
            PendingStatus expected) {
        return Arguments.of(scenario, binding(applied, declined), version(latest), expected);
    }

    private static EngagementBinding binding(int applied, int declined) {
        return new EngagementBinding(new FirmId("firm-a"), new EngagementId("eng-1"),
                new TemplateId("audit"), new Version(applied), version(declined), 1L);
    }

    private static Optional<Version> version(int value) {
        return value == NONE ? Optional.empty() : Optional.of(new Version(value));
    }

    private static UpdateAvailable offer(int from, int to, boolean includesDeclined) {
        return new UpdateAvailable(new Version(from), new Version(to), includesDeclined);
    }
}
