import {
    Alert,
    AlertDescription,
    AlertDescriptionProps,
    AlertIcon,
    AlertProps,
    AlertStatus,
    AlertTitle,
    AlertTitleProps,
} from "@chakra-ui/alert"
import { CloseButton } from "@chakra-ui/close-button"
import { Box } from "@chakra-ui/layout"
import React from "react"

type Props = {
    icon?: boolean
    title?: string
    description?: string | JSX.Element
    status?: AlertStatus
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    onClose?: () => any
    alertProps?: AlertProps
    descriptionProps?: AlertDescriptionProps
    titleProps?: AlertTitleProps
}

const AlertBox: React.FC<Props> = ({
    icon,
    title,
    description,
    onClose,
    alertProps,
    descriptionProps,
    titleProps,
    status,
}) => (
    <Alert status={status} fontSize="sm" {...(alertProps ?? {})}>
        {icon && <AlertIcon />}
        {title && <AlertTitle {...(titleProps ?? {})}>{title}</AlertTitle>}
        {description && <AlertDescription {...(descriptionProps ?? {})}>{description}</AlertDescription>}
        {onClose && <Box width="24px" />}
        {onClose && <CloseButton onClick={onClose} position="absolute" right="3px" top="3px" />}
    </Alert>
)

export default AlertBox
