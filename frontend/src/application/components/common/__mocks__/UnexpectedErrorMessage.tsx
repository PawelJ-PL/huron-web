import React from "react"

const UnexpectedErrorMessage: React.FC<{ error: Error }> = ({ error }) => (
    <div data-testid="UNEXPECTED_ERROR_MESSAGE_MOCK">{error.message}</div>
)

export default UnexpectedErrorMessage
