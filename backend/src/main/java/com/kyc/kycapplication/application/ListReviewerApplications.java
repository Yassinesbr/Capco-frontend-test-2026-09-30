package com.kyc.kycapplication.application;

import com.kyc.kycapplication.application.port.out.ReviewerApplicationQueryRepository;
import com.kyc.kycapplication.application.port.out.ReviewerApplicationQueryRepository.ReviewerApplicationPage;
import com.kyc.kycapplication.domain.ApplicationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

/** Validates bounded reviewer filters and produces UTC workload metrics. */
public final class ListReviewerApplications {
    private final ReviewerApplicationQueryRepository repository;
    private final Clock clock;

    public ListReviewerApplications(final ReviewerApplicationQueryRepository repository, final Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public ReviewerApplicationPage list(final String search, final String status, final int page, final int pageSize) {
        String normalizedSearch = search == null ? "" : search.trim();
        if (normalizedSearch.length() > 120 || page < 0 || pageSize < 1 || pageSize > 50
                || (long) page * pageSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid reviewer application query.");
        }
        ApplicationStatus parsedStatus = parseStatus(status);
        Instant monthStart = clock.instant().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS)
                .withDayOfMonth(1).toInstant();
        Instant monthEndExclusive = monthStart.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        return repository.find(normalizedSearch, parsedStatus, page, pageSize, monthStart, monthEndExclusive);
    }

    private static ApplicationStatus parseStatus(final String status) {
        if (status == null || status.isBlank() || status.equalsIgnoreCase("all")) return null;
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "draft" -> ApplicationStatus.DRAFT;
            case "submitted" -> ApplicationStatus.SUBMITTED;
            case "in-review" -> ApplicationStatus.IN_REVIEW;
            case "approved" -> ApplicationStatus.APPROVED;
            case "rejected" -> ApplicationStatus.REJECTED;
            default -> throw new IllegalArgumentException("Invalid reviewer application status.");
        };
    }
}
