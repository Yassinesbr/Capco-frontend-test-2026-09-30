import { Stack } from '@kyc/ui'
import { useNavigate } from 'react-router'

import {
  ApplicantJourneyPresentation,
  type ApplicantJourneyPresentationFixture,
} from './-applicant-journey-presentation'

export default function CurrentApplicationJourneyPage() {
  const navigate = useNavigate()
  const fixture = presentationFixture()

  return (
    <Stack spacing={2}>
      <ApplicantJourneyPresentation
        {...fixture}
        onPrimaryAction={() => {
          void navigate('/applications/prototype/personal-details')
        }}
      />
    </Stack>
  )
}

function presentationFixture(): ApplicantJourneyPresentationFixture {
  return {
    entryState: 'start',
    progress: [
      { step: 'personal-details', state: 'current' },
      { step: 'identity-and-address', state: 'remaining' },
    ],
  }
}
