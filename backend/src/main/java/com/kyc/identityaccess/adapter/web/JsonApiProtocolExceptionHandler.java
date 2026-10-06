package com.kyc.identityaccess.adapter.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts transport parsing and negotiation failures into safe JSON:API errors. */
@RestControllerAdvice
public class JsonApiProtocolExceptionHandler {

    private static final MediaType JSON_API = MediaType.parseMediaType("application/vnd.api+json");

    /** Reports malformed JSON without reflecting parser or implementation details. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<JsonApiErrorDocument> malformedDocument() {
        return error(HttpStatus.BAD_REQUEST, "malformed-document", "Malformed JSON:API document",
                "The request body is not a valid JSON:API document.");
    }

    /** Reports unsupported request representations with the mandatory JSON:API error media type. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<JsonApiErrorDocument> unsupportedMediaType() {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", "Unsupported media type",
                "Use application/vnd.api+json for JSON:API request bodies.");
    }

    /** Reports unacceptable response representations without implementation details. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<JsonApiErrorDocument> notAcceptable() {
        return error(HttpStatus.NOT_ACCEPTABLE, "not-acceptable", "Not acceptable",
                "Accept application/vnd.api+json for JSON:API responses.");
    }

    private static ResponseEntity<JsonApiErrorDocument> error(
            final HttpStatus status, final String code, final String title, final String detail) {
        return ResponseEntity.status(status).contentType(JSON_API)
                .body(JsonApiErrorDocument.one(Integer.toString(status.value()), code, title, detail));
    }
}
