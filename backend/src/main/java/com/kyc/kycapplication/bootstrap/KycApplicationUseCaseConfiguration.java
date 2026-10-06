package com.kyc.kycapplication.bootstrap;

import com.kyc.identityaccess.application.ApplicantJourney;
import com.kyc.identityaccess.application.port.out.CurrentApplicantJourney;
import com.kyc.kycapplication.application.FindCurrentApplication;
import com.kyc.kycapplication.application.GetApplicantApplicationStatus;
import com.kyc.kycapplication.application.GetApplicationForm;
import com.kyc.kycapplication.application.ListReviewerApplications;
import com.kyc.kycapplication.application.port.out.ReviewerApplicationQueryRepository;
import com.kyc.kycapplication.application.SaveApplicationForm;
import com.kyc.kycapplication.application.SaveDocumentEvidence;
import com.kyc.kycapplication.application.SubmitApplication;
import com.kyc.kycapplication.application.port.out.ApplicationSubmissionRepository;
import com.kyc.kycapplication.application.port.out.DocumentStorage;
import com.kyc.kycapplication.application.port.out.KycApplicationFormRepository;
import com.kyc.kycapplication.application.StartApplication;
import com.kyc.kycapplication.application.port.out.KycApplicationRepository;
import com.kyc.kycapplication.application.port.out.PrivacySafeApplicationAuditPublisher;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composes KYC Application use cases and the narrow Identity and Access journey bridge. */
@Configuration(proxyBeanMethods = false)
public class KycApplicationUseCaseConfiguration {

    /** Wires the active-draft query to the KYC Application persistence boundary. */
    @Bean
    FindCurrentApplication findCurrentApplication(final KycApplicationRepository repository) {
        return new FindCurrentApplication(repository);
    }

    @Bean
    GetApplicantApplicationStatus getApplicantApplicationStatus(final KycApplicationRepository repository) {
        return new GetApplicantApplicationStatus(repository);
    }

    @Bean
    ListReviewerApplications listReviewerApplications(final ReviewerApplicationQueryRepository repository,
            final Clock clock) {
        return new ListReviewerApplications(repository, clock);
    }

    @Bean
    SubmitApplication submitApplication(
            final KycApplicationRepository applicationRepository,
            final KycApplicationFormRepository formRepository,
            final ApplicationSubmissionRepository submissionRepository,
            final Clock clock) {
        return new SubmitApplication(applicationRepository, formRepository, submissionRepository, clock);
    }

    /** Wires idempotent draft start to persistence and the system clock. */
    @Bean
    StartApplication startApplication(
            final KycApplicationRepository repository,
            final PrivacySafeApplicationAuditPublisher auditPublisher,
            final Clock clock) {
        return new StartApplication(repository, auditPublisher, clock);
    }

    /** Wires the owner-scoped form query. */
    @Bean
    GetApplicationForm getApplicationForm(
            final KycApplicationRepository applicationRepository,
            final KycApplicationFormRepository formRepository) {
        return new GetApplicationForm(applicationRepository, formRepository);
    }

    /** Wires atomic step saves through the form persistence boundary. */
    @Bean
    SaveApplicationForm saveApplicationForm(
            final KycApplicationRepository applicationRepository,
            final KycApplicationFormRepository formRepository,
            final Clock clock) {
        return new SaveApplicationForm(applicationRepository, formRepository, clock);
    }

    /** Wires bounded document evidence storage and form persistence. */
    @Bean
    SaveDocumentEvidence saveDocumentEvidence(
            final KycApplicationRepository applicationRepository,
            final KycApplicationFormRepository formRepository,
            final DocumentStorage documentStorage,
            final Clock clock) {
        return new SaveDocumentEvidence(applicationRepository, formRepository, documentStorage, clock);
    }

    /** Supplies the KYC-owned post-sign-in route without moving lifecycle policy into Identity Access. */
    @Bean
    CurrentApplicantJourney currentApplicantJourney(final FindCurrentApplication findCurrentApplication) {
        return accountId -> findCurrentApplication.findFor(new com.kyc.kycapplication.domain.ApplicantId(accountId))
                .map(application -> new ApplicantJourney(ApplicationJourneyMapper.href(application)))
                .orElseGet(() -> new ApplicantJourney("/applications/current"));
    }
}
