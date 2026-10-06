package com.kyc.kycapplication.domain;

import java.util.EnumSet;
import java.util.Set;

/** Controlled Applicant KYC field inventory and its owning step. */
public enum FormField {
    NAME(ApplicationStep.PERSONAL_DETAILS, "name"),
    DATE_OF_BIRTH(ApplicationStep.PERSONAL_DETAILS, "dateOfBirth"),
    COUNTRY(ApplicationStep.PERSONAL_DETAILS, "country"),
    NATIONALITY(ApplicationStep.PERSONAL_DETAILS, "nationality"),
    EMAIL(ApplicationStep.PERSONAL_DETAILS, "email"),
    PHONE(ApplicationStep.PERSONAL_DETAILS, "phone"),
    DOCUMENT_TYPE(ApplicationStep.IDENTITY_AND_ADDRESS, "documentType"),
    DOCUMENT_NUMBER(ApplicationStep.IDENTITY_AND_ADDRESS, "documentNumber"),
    DOCUMENT_COUNTRY(ApplicationStep.IDENTITY_AND_ADDRESS, "documentCountry"),
    EXPIRY(ApplicationStep.IDENTITY_AND_ADDRESS, "expiry"),
    STREET(ApplicationStep.IDENTITY_AND_ADDRESS, "street"),
    CITY(ApplicationStep.IDENTITY_AND_ADDRESS, "city"),
    POSTAL(ApplicationStep.IDENTITY_AND_ADDRESS, "postal"),
    RESIDENTIAL_COUNTRY(ApplicationStep.IDENTITY_AND_ADDRESS, "residentialCountry"),
    CONSENT(ApplicationStep.PERSONAL_DETAILS, "consentConfirmed"),
    DOCUMENT_EVIDENCE(ApplicationStep.IDENTITY_AND_ADDRESS, "documentEvidence");

    private final ApplicationStep step;
    private final String wireName;

    FormField(final ApplicationStep step, final String wireName) {
        this.step = step;
        this.wireName = wireName;
    }

    /** Returns the form step that owns this field. */
    public ApplicationStep step() {
        return step;
    }

    /** Returns the lower-camel-case API field name. */
    public String wireName() {
        return wireName;
    }

    /** Returns the persisted answer fields for one step, excluding boolean consent and evidence. */
    public static Set<FormField> answerFields(final ApplicationStep step) {
        EnumSet<FormField> fields = EnumSet.noneOf(FormField.class);
        for (FormField field : values()) {
            if (field.step == step && field != CONSENT && field != DOCUMENT_EVIDENCE) {
                fields.add(field);
            }
        }
        return Set.copyOf(fields);
    }
}
