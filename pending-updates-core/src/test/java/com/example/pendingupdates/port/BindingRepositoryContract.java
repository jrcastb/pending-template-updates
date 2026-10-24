package com.example.pendingupdates.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pendingupdates.domain.BindingSnapshot;
import com.example.pendingupdates.domain.BindingSnapshot.Cause;
import com.example.pendingupdates.domain.BindingUpdate.Created;
import com.example.pendingupdates.domain.BindingUpdate.Ignored;
import com.example.pendingupdates.domain.EngagementBinding;
import com.example.pendingupdates.domain.EngagementBinding.RejectedSnapshot;
import com.example.pendingupdates.domain.EngagementId;
import com.example.pendingupdates.domain.FirmId;
import com.example.pendingupdates.domain.FirmScope;
import com.example.pendingupdates.domain.TemplateId;
import com.example.pendingupdates.domain.Version;
import com.example.pendingupdates.port.BindingRepository.FirmMismatch;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behaviour every {@link BindingRepository} adapter must satisfy: it stores the domain's decision
 * as one read-decide-write, and it isolates firms. The decision rules themselves are tested in
 * {@code EngagementBindingTest}. Proves the port contract only; database-level isolation (Postgres
 * RLS) and concurrent writers need their own integration tests.
 */
public abstract class BindingRepositoryContract {

    private static final FirmId FIRM_A = new FirmId("firm-a");
    private static final FirmId FIRM_B = new FirmId("firm-b");
    private static final FirmScope SCOPE_A = new FirmScope(FIRM_A);
    private static final FirmScope SCOPE_B = new FirmScope(FIRM_B);
    private static final EngagementId ENG_1 = new EngagementId("eng-1");
    private static final TemplateId AUDIT = new TemplateId("audit");

    private BindingRepository repository;

    protected abstract BindingRepository newRepository();

    @BeforeEach
    void setUp() {
        repository = newRepository();
    }

    @Test
    void upsertStoresWhatTheDomainDecides() {
        // given
        var first = repository.upsertIfNewer(SCOPE_A, snapshot(FIRM_A, ENG_1, AUDIT, 2, 3));

        // when a stale snapshot follows
        var second = repository.upsertIfNewer(SCOPE_A, snapshot(FIRM_A, ENG_1, AUDIT, 1, 2));

        // then the created row is kept
        assertThat(first).isInstanceOf(Created.class);
        assertThat(second).isEqualTo(new Ignored(Ignored.Reason.STALE));
        assertThat(repository.find(SCOPE_A, ENG_1)).map(EngagementBinding::applied)
                .contains(new Version(3));
    }

    @Test
    void rejectedSnapshotLeavesTheRowUnchanged() {
        // given
        repository.upsertIfNewer(SCOPE_A, snapshot(FIRM_A, ENG_1, AUDIT, 1, 2));

        // when the same revision arrives with different content
        assertThatThrownBy(() ->
                repository.upsertIfNewer(SCOPE_A, snapshot(FIRM_A, ENG_1, AUDIT, 1, 5)))
                .isInstanceOf(RejectedSnapshot.class);

        // then
        assertThat(repository.find(SCOPE_A, ENG_1)).map(EngagementBinding::applied)
                .contains(new Version(2));
    }

    @Test
    void firmAScopeNeverReadsFirmBRows() {
        // given
        repository.upsertIfNewer(SCOPE_B, snapshot(FIRM_B, ENG_1, AUDIT, 1, 2));

        // then
        assertThat(repository.find(SCOPE_A, ENG_1)).isEmpty();
        assertThat(repository.findByTemplate(SCOPE_A, AUDIT)).isEmpty();
    }

    @Test
    void sameEngagementIdInTwoFirmsGivesTwoIndependentRows() {
        // given
        repository.upsertIfNewer(SCOPE_A, snapshot(FIRM_A, ENG_1, AUDIT, 1, 2));
        repository.upsertIfNewer(SCOPE_B, snapshot(FIRM_B, ENG_1, AUDIT, 1, 7));

        // then
        assertThat(repository.find(SCOPE_A, ENG_1)).map(EngagementBinding::applied)
                .contains(new Version(2));
        assertThat(repository.find(SCOPE_B, ENG_1)).map(EngagementBinding::applied)
                .contains(new Version(7));
    }

    @Test
    void snapshotOfAnotherFirmIsRejectedAndNothingIsStored() {
        // when firm A's snapshot is written under firm B's scope
        assertThatThrownBy(() ->
                repository.upsertIfNewer(SCOPE_B, snapshot(FIRM_A, ENG_1, AUDIT, 1, 2)))
                .isInstanceOf(FirmMismatch.class);

        // then neither firm has a row
        assertThat(repository.find(SCOPE_A, ENG_1)).isEmpty();
        assertThat(repository.find(SCOPE_B, ENG_1)).isEmpty();
    }

    @Test
    void findByTemplateReturnsOnlyThatFirmsEngagementsOnThatTemplate() {
        // given
        EngagementId eng2 = new EngagementId("eng-2");
        repository.upsertIfNewer(SCOPE_A, snapshot(FIRM_A, ENG_1, AUDIT, 1, 2));
        repository.upsertIfNewer(SCOPE_A, snapshot(FIRM_A, eng2, new TemplateId("review"), 1, 2));
        repository.upsertIfNewer(SCOPE_B, snapshot(FIRM_B, eng2, AUDIT, 1, 2));

        // then
        assertThat(repository.findByTemplate(SCOPE_A, AUDIT))
                .extracting(EngagementBinding::engagementId)
                .containsExactly(ENG_1);
    }

    private static BindingSnapshot snapshot(FirmId firm, EngagementId engagement,
            TemplateId template, long revision, int applied) {
        return new BindingSnapshot(firm, engagement, template, new Version(applied),
                Optional.empty(), revision, Cause.OPENED);
    }
}
