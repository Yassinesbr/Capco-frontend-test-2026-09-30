package com.kyc.kycapplication.adapter.web;

import tools.jackson.databind.JsonNode;
import com.kyc.identityaccess.adapter.security.SessionCookieFactory;
import com.kyc.identityaccess.application.AuthorizeApplicantAccess;
import com.kyc.identityaccess.application.AuthorizedApplicant;
import com.kyc.identityaccess.application.ProtectedWorkArea;
import com.kyc.identityaccess.domain.SessionId;
import com.kyc.kycapplication.application.GetApplicationForm;
import com.kyc.kycapplication.application.SaveApplicationForm;
import com.kyc.kycapplication.application.SaveDocumentEvidence;
import com.kyc.kycapplication.domain.ApplicantId;
import com.kyc.kycapplication.domain.ApplicationForm;
import com.kyc.kycapplication.domain.ApplicationId;
import com.kyc.kycapplication.domain.ApplicationStep;
import com.kyc.kycapplication.domain.FormField;
import com.kyc.kycapplication.domain.MissingRequiredField;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Owner-scoped JSON:API and multipart adapters for the two-step Applicant KYC form. */
@RestController
@RequestMapping("/api/v1/applicant-applications/{applicationId}")
public class ApplicantKycFormController {

    private static final String JSON_API = "application/vnd.api+json";
    private final AuthorizeApplicantAccess authorizeApplicantAccess;
    private final GetApplicationForm getApplicationForm;
    private final SaveApplicationForm saveApplicationForm;
    private final SaveDocumentEvidence saveDocumentEvidence;

    /** Creates the form HTTP adapter from the established authorization and use-case boundaries. */
    public ApplicantKycFormController(
            final AuthorizeApplicantAccess authorizeApplicantAccess,
            final GetApplicationForm getApplicationForm,
            final SaveApplicationForm saveApplicationForm,
            final SaveDocumentEvidence saveDocumentEvidence) {
        this.authorizeApplicantAccess = authorizeApplicantAccess;
        this.getApplicationForm = getApplicationForm;
        this.saveApplicationForm = saveApplicationForm;
        this.saveDocumentEvidence = saveDocumentEvidence;
    }

    @GetMapping(value = "/form", produces = JSON_API)
    public ResponseEntity<FormDocument> form(
            @PathVariable final UUID applicationId,
            @CookieValue(name = SessionCookieFactory.SESSION_COOKIE_NAME, required = false) final String sessionCookie) {
        GetApplicationForm.ApplicationFormResult result = getApplicationForm.find(
                applicantId(sessionCookie), new ApplicationId(applicationId));
        return json(HttpStatus.OK, FormDocument.from(result.form()));
    }

    @PatchMapping(value = "/form", consumes = JSON_API, produces = JSON_API)
    public ResponseEntity<FormDocument> update(
            @PathVariable final UUID applicationId,
            @RequestBody final FormUpdateDocument request,
            @CookieValue(name = SessionCookieFactory.SESSION_COOKIE_NAME, required = false) final String sessionCookie) {
        if (request == null || request.data() == null || request.data().attributes() == null) {
            throw new IllegalArgumentException("form data is required");
        }
        FormUpdateAttributes attributes = request.data().attributes();
        ApplicationStep step = parseStep(attributes.step());
        Map<FormField, String> answers = parseAnswers(step, attributes.answers());
        Boolean consent = consent(attributes.answers());
        GetApplicationForm.ApplicationFormResult result = saveApplicationForm.save(
                applicantId(sessionCookie), new ApplicationId(applicationId), step, answers, consent, attributes.version());
        return json(HttpStatus.OK, FormDocument.from(result.form()));
    }

    @GetMapping(value = "/review", produces = JSON_API)
    public ResponseEntity<ReviewDocument> review(
            @PathVariable final UUID applicationId,
            @CookieValue(name = SessionCookieFactory.SESSION_COOKIE_NAME, required = false) final String sessionCookie) {
        GetApplicationForm.ApplicationFormResult result = getApplicationForm.find(
                applicantId(sessionCookie), new ApplicationId(applicationId));
        return json(HttpStatus.OK, ReviewDocument.from(result.form()));
    }

    @PostMapping(value = "/document-evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = JSON_API)
    public ResponseEntity<DocumentEvidenceDocument> documentEvidence(
            @PathVariable final UUID applicationId,
            @RequestPart("file") final MultipartFile file,
            @CookieValue(name = SessionCookieFactory.SESSION_COOKIE_NAME, required = false) final String sessionCookie)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new com.kyc.kycapplication.application.InvalidDocumentEvidenceException("A document is required.");
        }
        GetApplicationForm.ApplicationFormResult result = saveDocumentEvidence.save(
                applicantId(sessionCookie), new ApplicationId(applicationId), file.getInputStream(),
                file.getContentType(), file.getSize());
        return json(HttpStatus.CREATED, DocumentEvidenceDocument.from(result.form()));
    }

    private ApplicantId applicantId(final String sessionCookie) {
        if (sessionCookie == null || sessionCookie.isBlank()) {
            throw new ApplicantAuthenticationRequiredException();
        }
        AuthorizedApplicant applicant = authorizeApplicantAccess.authorize(
                new SessionId(sessionCookie), ProtectedWorkArea.APPLICANT);
        return new ApplicantId(applicant.accountId());
    }

    private static ApplicationStep parseStep(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("step is required");
        }
        return switch (value) {
            case "personal-details" -> ApplicationStep.PERSONAL_DETAILS;
            case "identity-and-address" -> ApplicationStep.IDENTITY_AND_ADDRESS;
            default -> throw new IllegalArgumentException("step is not supported");
        };
    }

    private static Map<FormField, String> parseAnswers(final ApplicationStep step, final Map<String, JsonNode> answers) {
        Map<FormField, String> parsed = new LinkedHashMap<>();
        if (answers == null) {
            return parsed;
        }
        for (Map.Entry<String, JsonNode> entry : answers.entrySet()) {
            FormField field = field(entry.getKey());
            if (field == FormField.CONSENT || field == FormField.DOCUMENT_EVIDENCE) {
                continue;
            }
            if (field.step() != step) {
                throw new IllegalArgumentException("answer does not belong to step");
            }
            parsed.put(field, entry.getValue() == null || entry.getValue().isNull() ? null : entry.getValue().asText());
        }
        return parsed;
    }

    private static Boolean consent(final Map<String, JsonNode> answers) {
        if (answers == null || !answers.containsKey("consentConfirmed")) {
            return null;
        }
        JsonNode value = answers.get("consentConfirmed");
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException("consentConfirmed must be boolean");
        }
        return value.booleanValue();
    }

    private static FormField field(final String wireName) {
        for (FormField field : FormField.values()) {
            if (field.wireName().equals(wireName)) {
                return field;
            }
        }
        throw new IllegalArgumentException("answer field is not supported");
    }

    private static <T> ResponseEntity<T> json(final HttpStatus status, final T body) {
        return ResponseEntity.status(status).contentType(MediaType.parseMediaType(JSON_API)).body(body);
    }

    public record FormUpdateDocument(FormUpdateResource data) {
    }

    public record FormUpdateResource(String type, String id, FormUpdateAttributes attributes) {
    }

    public record FormUpdateAttributes(String step, Map<String, JsonNode> answers, Integer version) {
    }

    public record FormDocument(FormResource data) {
        static FormDocument from(final ApplicationForm form) {
            return new FormDocument(new FormResource("applicant-application-forms",
                    form.applicationId().value().toString(), FormAttributes.from(form)));
        }
    }

    public record FormResource(String type, String id, FormAttributes attributes) {
    }

    public record FormAttributes(String status, String currentStep, java.util.List<StepProgressResponse> steps,
                                 Map<String, Object> answers, DocumentEvidenceSummary documentEvidence, int version) {
        static FormAttributes from(final ApplicationForm form) {
            Map<String, Object> answers = new LinkedHashMap<>();
            form.answers().forEach((field, value) -> answers.put(field.wireName(), value));
            answers.put("consentConfirmed", form.consentConfirmed());
            return new FormAttributes("draft", kebab(form.currentStep().name()),
                    form.progress().stream().map(StepProgressResponse::from).toList(), answers,
                    new DocumentEvidenceSummary(form.documentEvidencePresent()), form.version());
        }
    }

    public record StepProgressResponse(String step, String state) {
        static StepProgressResponse from(final com.kyc.kycapplication.domain.StepProgress progress) {
            String state = progress.state() == com.kyc.kycapplication.domain.StepState.NOT_STARTED
                    ? "remaining" : kebab(progress.state().name());
            return new StepProgressResponse(kebab(progress.step().name()), state);
        }
    }

    public record DocumentEvidenceSummary(boolean present) {
    }

    public record ReviewDocument(ReviewResource data) {
        static ReviewDocument from(final ApplicationForm form) {
            return new ReviewDocument(new ReviewResource("applicant-application-reviews",
                    form.applicationId().value().toString(), new ReviewAttributes(form.ready(),
                    form.missingRequiredFields().stream().map(MissingRequiredFieldResponse::from).toList())));
        }
    }

    public record ReviewResource(String type, String id, ReviewAttributes attributes) {
    }

    public record ReviewAttributes(boolean ready, java.util.List<MissingRequiredFieldResponse> missingRequiredFields) {
    }

    public record MissingRequiredFieldResponse(String step, String field) {
        static MissingRequiredFieldResponse from(final MissingRequiredField missing) {
            return new MissingRequiredFieldResponse(kebab(missing.step().name()), missing.field());
        }
    }

    public record DocumentEvidenceDocument(DocumentEvidenceResource data) {
        static DocumentEvidenceDocument from(final ApplicationForm form) {
            return new DocumentEvidenceDocument(new DocumentEvidenceResource(
                    "applicant-application-document-evidence", form.applicationId().value().toString(),
                    new DocumentEvidenceSummary(form.documentEvidencePresent())));
        }
    }

    public record DocumentEvidenceResource(String type, String id, DocumentEvidenceSummary attributes) {
    }

    private static String kebab(final String enumName) {
        return enumName.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
