import React from "react"

const EmptyPlaceholder: React.FC<{ text: string }> = ({ text }) => (
    <div data-testid="EMPTY_PLACEHOLDER_MOCK">{text}</div>
)

export default EmptyPlaceholder
