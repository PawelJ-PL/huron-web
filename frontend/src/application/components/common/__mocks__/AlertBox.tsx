import { AlertStatus } from "@chakra-ui/react"
import React from "react"

type Props = {
    icon?: boolean
    title?: string
    description?: string
    status?: AlertStatus
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    onClose?: () => any
}

const AlertBox: React.FC<Props> = ({ icon, title, description, status }) => (
    <div data-testid="ALERT_BOX_MOCK">
        <div>ICON: {icon}</div>
        <div>TITLE: {title}</div>
        <div>DESCRIPTION: {description}</div>
        <div>STATUS: {status}</div>
    </div>
)

export default AlertBox
