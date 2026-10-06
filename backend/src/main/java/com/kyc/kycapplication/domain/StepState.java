package com.kyc.kycapplication.domain;

/** Truthful progress state for one ordered application step. */
public enum StepState {
    /** The step has not yet been reached. */
    NOT_STARTED,
    /** The earliest incomplete step. */
    CURRENT,
    /** Completion was recorded by the owning future step behavior. */
    COMPLETE
}
