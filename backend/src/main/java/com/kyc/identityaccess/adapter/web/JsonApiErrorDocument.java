package com.kyc.identityaccess.adapter.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Safe JSON:API error envelope used by Identity Access HTTP adapters. */
public record JsonApiErrorDocument(List<JsonApiError> errors) {

    static JsonApiErrorDocument one(
            final String status, final String code, final String title, final String detail) {
        return new JsonApiErrorDocument(List.of(new JsonApiError(status, code, title, detail, null)));
    }

    static JsonApiErrorDocument field(
            final String status, final String code, final String detail, final String pointer) {
        return new JsonApiErrorDocument(List.of(new JsonApiError(
                status, code, "Invalid attribute", detail, new JsonApiErrorSource(pointer))));
    }

    /** A deliberately public and client-actionable JSON:API error object. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonApiError(
            String status, String code, String title, String detail, JsonApiErrorSource source) {
    }

    /** Identifies the invalid JSON:API request member. */
    public record JsonApiErrorSource(String pointer) {
    }
}
