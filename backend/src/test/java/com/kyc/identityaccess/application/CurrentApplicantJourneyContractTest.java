package com.kyc.identityaccess.application;

import com.kyc.identityaccess.application.port.out.CurrentApplicantJourney;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentApplicantJourneyContractTest {

    @Test
    void providerContractUsesOnlyApplicantIdentityAndReturnsAnApplicationLocalDestination() {
        UUID applicantId = UUID.randomUUID();
        CurrentApplicantJourney testProvider = suppliedApplicantId -> {
            assertThat(suppliedApplicantId).isEqualTo(applicantId);
            return new ApplicantJourney("/application/current");
        };

        ApplicantJourney journey = testProvider.findFor(applicantId);

        assertThat(journey.href()).isEqualTo("/application/current");
    }

    @Test
    void journeyCannotReturnAnExternalOrProtocolRelativeDestination() {
        assertThatThrownBy(() -> new ApplicantJourney("https://untrusted.example"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicantJourney("//untrusted.example"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
