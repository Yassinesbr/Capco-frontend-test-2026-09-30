package com.kyc.kycapplication.domain;

import java.util.Objects;

/** Text-equivalent-friendly progress information for one application step. */
public record StepProgress(ApplicationStep step, StepState state) {

    /** Rejects incomplete progress facts. */
    public StepProgress {
        Objects.requireNonNull(step, "step must not be null");
        Objects.requireNonNull(state, "state must not be null");
    }
}
