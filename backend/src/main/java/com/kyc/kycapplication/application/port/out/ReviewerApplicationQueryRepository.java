package com.kyc.kycapplication.application.port.out;

import com.kyc.kycapplication.domain.ApplicationStatus;
import java.time.Instant;
import java.util.List;

/** Read-only boundary for minimized reviewer overview projections. */
public interface ReviewerApplicationQueryRepository {
    ReviewerApplicationPage find(String search, ApplicationStatus status, int page, int pageSize,
            Instant monthStart, Instant monthEndExclusive);

    record ReviewerApplicationPage(List<ReviewerApplicationRow> applications, int pendingReview, int inProgress,
            int approvedThisMonth, long totalItems) {
    }

    record ReviewerApplicationRow(String id, String applicantName, Instant submittedAt, ApplicationStatus status) {
    }
}
