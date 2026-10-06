package com.kyc.kycapplication.adapter.web;

import java.util.List;

/** Safe JSON:API error envelope for KYC Application HTTP adapters. */
public record JsonApiErrorDocument(List<JsonApiError> errors) {

    /** Client-actionable safe error without internal details. */
    public static JsonApiErrorDocument one(
            final String status, final String code, final String title, final String detail) {
        return new JsonApiErrorDocument(List.of(new JsonApiError(status, code, title, detail)));
    }

    static JsonApiErrorDocument authenticationRequired() {
        return new JsonApiErrorDocument(List.of(new JsonApiError(
                "401", "authentication-required", "Authentication required", "Sign in to continue.")));
    }

    static JsonApiErrorDocument notFound() {
        return new JsonApiErrorDocument(List.of(new JsonApiError(
                "404", "application-not-found", "Application not found", "The requested application is not available.")));
    }

    static JsonApiErrorDocument conflict() {
        return new JsonApiErrorDocument(List.of(new JsonApiError(
                "409", "form-version-conflict", "Form changed", "Reload the form and try again.")));
    }

    static JsonApiErrorDocument unprocessable(final String detail) {
        return new JsonApiErrorDocument(List.of(new JsonApiError(
                "422", "unprocessable-form", "Form could not be saved", detail)));
    }

    static JsonApiErrorDocument incomplete(final List<String> missingFields) {
        String fields = String.join(", ", missingFields);
        return new JsonApiErrorDocument(List.of(new JsonApiError(
                "422", "required-fields-missing", "Required fields are missing",
                "Complete the required fields before submitting: " + fields + ".")));
    }

    static JsonApiErrorDocument submissionUnavailable() {
        return new JsonApiErrorDocument(List.of(new JsonApiError(
                "503", "submission-unavailable", "Submission unavailable",
                "Your application was not submitted. Please retry later.")));
    }

    static JsonApiErrorDocument internalError() {
        return new JsonApiErrorDocument(List.of(new JsonApiError(
                "500", "internal-error", "Request failed", "The request could not be completed.")));
    }

    static JsonApiErrorDocument unsupportedMediaType() {
        return new JsonApiErrorDocument(List.of(new JsonApiError(
                "415", "unsupported-document", "Unsupported document", "Upload a JPG or PNG image.")));
    }

    /** Client-actionable error without internal details. */
    public record JsonApiError(String status, String code, String title, String detail) {
    }
}
