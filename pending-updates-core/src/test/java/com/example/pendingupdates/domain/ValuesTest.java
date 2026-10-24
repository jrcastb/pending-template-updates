package com.example.pendingupdates.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    static Stream<Arguments> idConstructors() {
        return Stream.of(
                Arguments.of("FirmId", (Function<String, Object>) FirmId::new),
                Arguments.of("EngagementId", (Function<String, Object>) EngagementId::new),
                Arguments.of("TemplateId", (Function<String, Object>) TemplateId::new));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("idConstructors")
    void idRejectsMissingOrBlankValue(String type, Function<String, Object> constructor) {
        assertThatThrownBy(() -> constructor.apply(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> constructor.apply("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> constructor.apply("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("idConstructors")
    void idsWithTheSameValueAreEqual(String type, Function<String, Object> constructor) {
        assertThat(constructor.apply("a-1")).isEqualTo(constructor.apply("a-1"));
        assertThat(constructor.apply("a-1")).isNotEqualTo(constructor.apply("a-2"));
    }

    @Test
    void firmScopeRequiresAFirm() {
        assertThatThrownBy(() -> new FirmScope(null)).isInstanceOf(NullPointerException.class);
    }
}
