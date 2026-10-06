package com.kyc.identityaccess.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicantSessionTest {

    @Test
    void revocationStopsSessionAuthorization() {
        Instant createdAt = Instant.parse("2026-09-22T12:00:00Z");
        ApplicantSession session = new ApplicantSession(
                new SessionId("opaque-session"),
                UUID.randomUUID(),
                Role.APPLICANT,
                createdAt,
                createdAt.plus(30, ChronoUnit.MINUTES),
                createdAt.plus(8, ChronoUnit.HOURS),
                null);

        assertThat(session.isActiveAt(createdAt.plus(1, ChronoUnit.MINUTES))).isTrue();
        assertThat(session.revoke(createdAt.plus(2, ChronoUnit.MINUTES))
                .isActiveAt(createdAt.plus(3, ChronoUnit.MINUTES))).isFalse();
    }
}
