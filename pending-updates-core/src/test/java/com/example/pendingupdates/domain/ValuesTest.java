package com.example.pendingupdates.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValuesTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void versionMustBePositive(int value) {
        assertThatThrownBy(() -> new Version(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void versionsAreOrderedByNumber() {
        // given
        List<Version> versions = List.of(new Version(5), new Version(2), new Version(3));

        // when
        List<Version> sorted = versions.stream().sorted().toList();

        // then
        assertThat(sorted).containsExactly(new Version(2), new Version(3), new Version(5));
    }

    // FirmId, EngagementId and TemplateId share the same validation; one is tested.
    @Test
    void idRejectsMissingOrBlankValue() {
        assertThatThrownBy(() -> new FirmId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FirmId("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FirmId("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void firmScopeRequiresAFirm() {
        assertThatThrownBy(() -> new FirmScope(null)).isInstanceOf(NullPointerException.class);
    }
}
