import React from "react"

const PageLoader: React.FC<{ title: string }> = ({ title }) => <div data-testid="LOADER_MOCK">{title}</div>

export default PageLoader
