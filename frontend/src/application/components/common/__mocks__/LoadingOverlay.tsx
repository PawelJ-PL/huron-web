import React from "react"

const LoadingOverlay: React.FC<{ active: boolean; text: string }> = ({ active, text }) => (
    <div data-testid="LOADING_OVERLAY_MOCK" aria-hidden={!active}>
        {text}
    </div>
)

export default LoadingOverlay
