import React from "react"
import { withTranslation, WithTranslation } from "react-i18next"
import AlertBox from "./AlertBox"
import capitalize from "lodash/capitalize"
import { AlertProps } from "@chakra-ui/alert"
import { Accordion, AccordionButton, AccordionIcon, AccordionItem, AccordionPanel } from "@chakra-ui/accordion"
import { Box, Heading } from "@chakra-ui/layout"
import { HTTPError } from "ky"
import { ZodError } from "zod"

type Props = {
    error: Error
    onClose?: () => void
    alertProps?: AlertProps
} & Pick<WithTranslation, "t">

const UnexpectedErrorMessage: React.FC<Props> = ({ error, t, alertProps, onClose }) => {
    let detailsContent: JSX.Element

    if (error instanceof HTTPError) {
        detailsContent = (
            <Box>
                <Box>
                    <b>HTTP Error</b>
                </Box>
                <Box>
                    <b>URL:</b> {error.request.url}
                </Box>
                <Box>
                    <b>Method:</b> {error.request.method}
                </Box>
                <Box>
                    <b>Status:</b> {error.response.status}
                </Box>
                <Box>
                    <b>TraceId:</b> {error.response.headers.get("trace-id")}
                </Box>
            </Box>
        )
    } else if (error instanceof ZodError) {
        detailsContent = (
            <Box>
                <Box>
                    <b>Schema Error</b>
                </Box>
                <Box>{JSON.stringify(error.issues)}</Box>
            </Box>
        )
    } else {
        detailsContent = (
            <Box>
                <Box>
                    <b>Generic Error</b>
                </Box>
                <Box>{error.message}</Box>
            </Box>
        )
    }

    const details =
        detailsContent === undefined ? undefined : (
            <Accordion allowToggle={true}>
                <AccordionItem isFocusable={false}>
                    <AccordionButton>
                        <Heading as="h6" size="xs">
                            {t("common:details")}
                        </Heading>
                        <AccordionIcon />
                    </AccordionButton>
                    <AccordionPanel>{detailsContent}</AccordionPanel>
                </AccordionItem>
            </Accordion>
        )

    return (
        <AlertBox
            alertProps={{ ...(alertProps ?? {}), flexDirection: "column", alignItems: "flex-start" }}
            descriptionProps={{ width: "100%" }}
            onClose={onClose}
            title={capitalize(t("common:unexpected-error"))}
            description={details}
            status="error"
            icon={false}
        />
    )
}

export default withTranslation()(UnexpectedErrorMessage)
