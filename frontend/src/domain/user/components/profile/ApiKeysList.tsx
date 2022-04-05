import React, { useEffect, useState } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import EmptyPlaceholder from "../../../../application/components/common/EmptyPlaceholder"
import { ApiKeyDescription } from "../../types/ApiKey"
import { IoKeySharp } from "react-icons/io5"
import { Button, IconButton } from "@chakra-ui/button"
import ResponsiveTable, { KeyValues } from "../../../../application/components/common/responsive_table/ResponsiveTable"
import { ImEye } from "react-icons/im"
import { ImEyeBlocked } from "react-icons/im"
import { GrCopy } from "react-icons/gr"
import { Box, Flex, Text } from "@chakra-ui/layout"
import { useClipboard, useDisclosure } from "@chakra-ui/hooks"
import { Checkbox } from "@chakra-ui/react"
import formatDistanceToNowStrict from "date-fns/formatDistanceToNowStrict"
import format from "date-fns/format"
import isPast from "date-fns/isPast"
import { dateLocaleForLang } from "../../../../application/localization/utils"
import { BsTrash } from "react-icons/bs"
import { AppState } from "../../../../application/store"
import { Dispatch } from "redux"
import { ApiKeyUpdateData } from "../../api/UsersApi"
import {
    createApiKeyAction,
    deleteApiKeyAction,
    resetCreateApiKeyStatusAction,
    resetDeleteApiKeyStatusAction,
    resetUpdateApiKeyStatusAction,
    updateApiKeyAction,
} from "../../store/Actions"
import { connect } from "react-redux"
import { useToast } from "@chakra-ui/react"
import { Editable, EditableInput, EditablePreview } from "@chakra-ui/editable"
import Confirmation from "../../../../application/components/common/Confirmation"
import ApiKeyExpirationDateModal from "./ApiKeyExpirationDateModal"
import NewApiKeyModal from "./NewApiKeyModal"
import { API_KEY_EXPIRATION_TIME, API_KEY_VALUE, DISABLE_API_KEY_CHECKBOX } from "./testids"

type Props = { apiKeys: ApiKeyDescription[] } & Pick<WithTranslation, "t" | "i18n"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps>

export const ApiKeyList: React.FC<Props> = ({
    apiKeys,
    t,
    i18n,
    resetUpdateStatus,
    resetDeleteStatus,
    updateKey,
    deleteKey,
    updateResult,
    deleteResult,
    createApiKey,
    resetCreateStatus,
    createResult,
}) => {
    useEffect(() => {
        resetUpdateStatus()
        resetDeleteStatus()
        resetCreateStatus()
        return () => {
            resetUpdateStatus()
            resetDeleteStatus()
            resetCreateStatus()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const toast = useToast({ position: "top", isClosable: true })

    useEffect(() => {
        if (updateResult.status === "FAILED") {
            toast({ title: t("profile-page:api-key-update-failed"), status: "warning" })
            resetUpdateStatus()
        }
    }, [updateResult, t, resetUpdateStatus, toast])

    useEffect(() => {
        if (deleteResult.status === "FAILED") {
            toast({ title: t("profile-page:api-key-delete-failed"), status: "warning" })
            resetDeleteStatus()
        }
    }, [deleteResult, t, resetDeleteStatus, toast])

    useEffect(() => {
        if (createResult.status === "FAILED") {
            toast({ title: t("profile-page:api-key-create-failed"), status: "warning" })
            resetCreateStatus()
        }
    }, [createResult, t, toast, resetCreateStatus])

    const [deleteRequested, setDeleteRequested] = useState<string | null>(null)
    const [expirationRequest, setExpirationRequest] = useState<ApiKeyDescription | null>(null)

    const newKeyDisclosure = useDisclosure()

    if (apiKeys.length < 1) {
        return (
            <EmptyPlaceholder text={t("profile-page:no-api-keys-created")} icon={IoKeySharp}>
                <NewApiKeyModal
                    isOpen={newKeyDisclosure.isOpen}
                    onClose={newKeyDisclosure.onClose}
                    onConfirm={(d) => createApiKey(d.description, d.validTo)}
                />
                <Button colorScheme="brand" size="sm" marginTop="0.3em" onClick={newKeyDisclosure.onOpen}>
                    {t("profile-page:create-api-key")}
                </Button>
            </EmptyPlaceholder>
        )
    } else {
        const data: KeyValues[] = [
            [
                t("profile-page:api-key-list-fields.api-key"),
                apiKeys.map((k) => <KeyField key={k.id} keyValue={k.key} t={t} />),
            ],
            [
                t("profile-page:api-key-list-fields.description"),
                apiKeys.map((k) => (
                    <DescriptionField
                        key={k.id}
                        description={k.description}
                        onChange={(newDesc) => updateKey(k.id, { description: newDesc })}
                    />
                )),
            ],
            [
                t("profile-page:api-key-list-fields.enabled"),
                apiKeys.map((k) => (
                    <EnabledField
                        key={k.id}
                        isEnabled={k.enabled}
                        onChange={(newValue) => updateKey(k.id, { enabled: newValue })}
                        canChange={updateResult.status !== "PENDING"}
                    />
                )),
            ],
            [
                t("profile-page:api-key-list-fields.valid-to"),
                apiKeys.map((k) => (
                    <ValidToField
                        key={k.id}
                        validTo={k.validTo}
                        t={t}
                        i18n={i18n}
                        onClick={() => setExpirationRequest(k)}
                        disabled={updateResult.status === "PENDING"}
                    />
                )),
            ],
            [
                t("profile-page:api-key-list-fields.created-at"),
                apiKeys.map((k) => <CreatedAtField key={k.id} createdAt={k.createdAt} i18n={i18n} />),
            ],
            [
                t("profile-page:api-key-list-fields.delete"),
                apiKeys.map((k) => (
                    <DeleteField
                        key={k.id}
                        keyId={k.id}
                        t={t}
                        onClick={() => setDeleteRequested(k.id)}
                        disabled={deleteResult.status === "PENDING"}
                    />
                )),
            ],
        ]

        return (
            <Box>
                <Confirmation
                    isOpen={deleteRequested !== null}
                    onClose={() => setDeleteRequested(null)}
                    onConfirm={() => (deleteRequested !== null ? deleteKey(deleteRequested) : void 0)}
                    content={t("profile-page:delete-api-key-confirmation")}
                />
                {expirationRequest && (
                    <ApiKeyExpirationDateModal
                        isOpen={expirationRequest !== null}
                        onClose={() => setExpirationRequest(null)}
                        onConfirm={(result) => updateKey(expirationRequest.id, { validTo: { value: result } })}
                        defaultValidTo={expirationRequest.validTo}
                    />
                )}
                <NewApiKeyModal
                    isOpen={newKeyDisclosure.isOpen}
                    onClose={newKeyDisclosure.onClose}
                    onConfirm={(d) => createApiKey(d.description, d.validTo)}
                />
                <Button colorScheme="brand" size="sm" onClick={newKeyDisclosure.onOpen} marginBottom="1em">
                    {t("profile-page:create-new-api-key")}
                </Button>
                <ResponsiveTable data={data} breakpointAt="xl" />
            </Box>
        )
    }
}

const KeyField: React.FC<{ keyValue: string } & Pick<WithTranslation, "t">> = ({ keyValue, t }) => {
    const { onCopy } = useClipboard(keyValue)
    const [visible, setVisible] = useState(false)
    const Icon = visible ? ImEyeBlocked : ImEye
    const showAriaLabel = visible ? t("profile-page:show-api-key-label") : t("profile-page:show-api-key-label")
    const copyAriaLabel = t("common:copy-imperative")

    return (
        <Flex alignItems="baseline">
            <IconButton aria-label={copyAriaLabel} icon={<GrCopy />} variant="ghost" onClick={onCopy} />
            <IconButton
                aria-label={showAriaLabel}
                icon={<Icon />}
                onClick={() => setVisible((prev) => !prev)}
                variant="ghost"
            />{" "}
            <Box maxWidth={["8ch", "30ch", null, "10ch"]} data-testid={API_KEY_VALUE}>
                {visible ? keyValue : keyValue.slice(0, 4) + "***"}
            </Box>
        </Flex>
    )
}

const DescriptionField: React.FC<{ description: string; onChange: (value: string) => void }> = ({
    description,
    onChange,
}) => {
    const [value, setValue] = useState<string>(description)

    const requiredPattern = /^[a-zA-Z0-9_ ]+$/

    const handleChange = (newValue: string) => {
        if (newValue.trim().length > 0 && newValue.trim().length <= 80 && requiredPattern.test(newValue)) {
            setValue(newValue)
        }
    }

    return (
        <Editable
            maxWidth={["100%", null, null, "12ch", null, "35ch"]}
            value={value}
            onChange={handleChange}
            onSubmit={onChange}
        >
            <EditablePreview maxWidth={["100%", null, null, "12ch", null, "35ch"]} cursor="pointer" />
            <EditableInput />
        </Editable>
    )
}

const EnabledField: React.FC<{ isEnabled: boolean; onChange: (newValue: boolean) => void; canChange: boolean }> = ({
    isEnabled,
    onChange,
    canChange,
}) => {
    return (
        <>
            <Checkbox
                defaultChecked={isEnabled}
                onChange={(e) => onChange(e.target.checked)}
                isDisabled={!canChange}
                data-testid={DISABLE_API_KEY_CHECKBOX}
            />
        </>
    )
}

const ValidToField: React.FC<
    { validTo?: string | null; onClick: () => void; disabled: boolean } & Pick<WithTranslation, "t" | "i18n">
> = ({ validTo, t, i18n, onClick, disabled }) => {
    const maybeDate = validTo ? new Date(validTo) : undefined
    const dateLocale = dateLocaleForLang(i18n.languages[0])
    const text = maybeDate
        ? `${formatDistanceToNowStrict(maybeDate, { addSuffix: true, locale: dateLocale })} (${format(maybeDate, "Pp", {
              locale: dateLocale,
          })})`
        : t("common:never")
    const textProps = maybeDate && isPast(maybeDate) ? { color: "red.500" } : {}

    return (
        <Text
            maxWidth="24ch"
            {...textProps}
            cursor={disabled ? "not-allowed" : "pointer"}
            onClick={disabled ? () => void 0 : onClick}
            data-testid={API_KEY_EXPIRATION_TIME}
        >
            {text}
        </Text>
    )
}

const CreatedAtField: React.FC<{ createdAt: string } & Pick<WithTranslation, "i18n">> = ({ createdAt, i18n }) => {
    const dateLocale = dateLocaleForLang(i18n.languages[0])
    const date = new Date(createdAt)

    return (
        <Box maxWidth="24ch">{`${formatDistanceToNowStrict(date, { addSuffix: true, locale: dateLocale })} (${format(
            date,
            "Pp",
            { locale: dateLocale }
        )})`}</Box>
    )
}

const DeleteField: React.FC<{ keyId: string; disabled: boolean; onClick: () => void } & Pick<WithTranslation, "t">> = ({
    keyId,
    t,
    onClick,
    disabled,
}) => {
    const ariaLabel = t("common:delete-imperative")

    return (
        <IconButton
            aria-label={ariaLabel}
            icon={<BsTrash />}
            variant="ghost"
            color="red.400"
            onClick={onClick}
            isDisabled={disabled}
        />
    )
}

const mapStateToProps = (state: AppState) => ({
    updateResult: state.users.updateApiKeyStatus,
    deleteResult: state.users.deleteApiKeyStatus,
    createResult: state.users.createApiKeyStatus,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    updateKey: (keyId: string, data: ApiKeyUpdateData) => dispatch(updateApiKeyAction.started({ keyId, data })),
    resetUpdateStatus: () => dispatch(resetUpdateApiKeyStatusAction()),
    deleteKey: (keyId: string) => dispatch(deleteApiKeyAction.started(keyId)),
    resetDeleteStatus: () => dispatch(resetDeleteApiKeyStatusAction()),
    createApiKey: (description: string, validTo?: string | null) =>
        dispatch(createApiKeyAction.started({ description, validTo: validTo ?? undefined })),
    resetCreateStatus: () => dispatch(resetCreateApiKeyStatusAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(ApiKeyList))
