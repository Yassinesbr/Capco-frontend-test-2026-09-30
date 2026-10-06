import { Alert, Button, Typography } from '@kyc/ui'
import { useNavigate } from 'react-router'

export default function SubmissionReceiptPage() {
  const navigate = useNavigate()
  const receipt = {
    message: 'Your application was received',
    nextStep: 'The review team will review your application.',
  }

  return (
    <div className="submission-receipt">
      <header className="submission-receipt__header">
        <Typography component="span" variant="subheading">
          KYC Application
        </Typography>
      </header>
      <main className="submission-receipt__main">
        <Typography component="p" variant="eyebrow" tone="accent">
          APPLICATION RECEIVED
        </Typography>
        <Typography component="h1" variant="heading">
          Application submitted
        </Typography>
        <Typography component="p" tone="muted">
          {receipt.nextStep}
        </Typography>
        <Alert
          role="status"
          severity="success"
          className="submission-receipt__alert"
        >
          {receipt.message}
        </Alert>
        <Button
          variant="quiet"
          onClick={() => void navigate('/applications/current')}
        >
          DONE
        </Button>
      </main>
    </div>
  )
}
