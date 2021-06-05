import React, { useEffect } from "react"
import { UserData } from "../../types/UserData"
import { z } from "zod"
import { isLanguage, languageSchema, nicknameSchema } from "../../types/fieldSchemas"
import { WithTranslation, withTranslation } from "react-i18next"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Box, Stack } from "@chakra-ui/layout"
import { FormControl, FormErrorMessage, FormLabel } from "@chakra-ui/form-control"
import { Input } from "@chakra-ui/input"
import capitalize from "lodash/capitalize"
import { Button } from "@chakra-ui/button"
import { Select } from "@chakra-ui/select"
import { AppState } from "../../../../application/store"
import { Dispatch } from "redux"
import { resetUpdateUserDataStatusAction, updateUserDataAction } from "../../store/Actions"
import { connect } from "react-redux"
import AlertBox from "../../../../application/components/common/AlertBox"
import { NoUpdatesProvides } from "../../../../application/api/ApiError"
import { UPDATE_PROFILE_LANGUAGE_SELECT } from "./testids"

const langOptions = [
    { value: "En", text: "English" },
    { value: "Pl", text: "Polski" },
]

type Props = { currentData: UserData } & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps>

export const UpdateProfileForm: React.FC<Props> = ({ t, currentData, resetUpdateStatus, updateProfile, updateResult }) => {
    useEffect(() => {
        resetUpdateStatus()
        return () => {
            resetUpdateStatus()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const formSchema = z.object({
        nickname: nicknameSchema(t),
        language: languageSchema,
    })

    type FormData = z.infer<typeof formSchema>

    const onSubmit = (data: FormData) => {
        const update = {
            nickName: dirtyFields.nickname ? data.nickname : undefined,
            language: dirtyFields.language ? data.language : undefined,
        }
        updateProfile(update)
    }

    const {
        register,
        handleSubmit,
        formState: { errors, isValidating, dirtyFields },
        reset,
    } = useForm<FormData>({
        resolver: zodResolver(formSchema),
        shouldUnregister: true,
        defaultValues: {
            nickname: currentData.nickName,
            language: isLanguage(currentData.language) ? currentData.language : undefined,
        },
    })

    useEffect(() => {
        if (updateResult.status === "FINISHED") {
            const lang = updateResult.params.language ?? currentData.language
            reset(
                {
                    nickname: updateResult.params.nickName ?? currentData.nickName,
                    language: isLanguage(lang) ? lang : "En",
                },
                {
                    keepDefaultValues: false,
                }
            )
        }
    }, [updateResult, currentData, reset])

    const renderError = (error: Error) => {
        if (error instanceof NoUpdatesProvides) {
            return (
                <AlertBox
                    title={t("profile-page:no-updates-provided-message")}
                    icon={true}
                    alertProps={{ marginBottom: "0.5em" }}
                    onClose={resetUpdateStatus}
                />
            )
        } else {
            return (
                <AlertBox
                    title={capitalize(t("common:unexpected-error"))}
                    status="error"
                    icon={true}
                    alertProps={{ marginBottom: "0.5em" }}
                    onClose={resetUpdateStatus}
                />
            )
        }
    }

    return (
        <Box>
            {updateResult.status === "FAILED" && renderError(updateResult.error)}
            <form onSubmit={handleSubmit(onSubmit)}>
                <Stack spacing="0.7rem" direction={["column", null, null, "row"]}>
                    <FormControl id="nickname" isRequired={true} isInvalid={errors.nickname !== undefined}>
                        <FormLabel fontSize="sm">{capitalize(t("common:nickname"))}</FormLabel>
                        <Input placeholder={capitalize(t("common:nickname"))} {...register("nickname")} />
                        <FormErrorMessage>{errors.nickname?.message}</FormErrorMessage>
                    </FormControl>

                    <FormControl id="language" isRequired={true}>
                        <FormLabel fontSize="sm">{capitalize(t("common:language"))}</FormLabel>
                        <Select {...register("language")} data-testid={UPDATE_PROFILE_LANGUAGE_SELECT}>
                            {langOptions.map((lang) => {
                                return (
                                    <option key={lang.value} value={lang.value}>
                                        {lang.text}
                                    </option>
                                )
                            })}
                        </Select>
                    </FormControl>
                </Stack>

                <Button
                    marginTop="1rem"
                    colorScheme="brand"
                    loadingText={capitalize(t("common:form-save"))}
                    isLoading={isValidating || updateResult.status === "PENDING"}
                    type="submit"
                    size="sm"
                >
                    {capitalize(t("common:form-save"))}
                </Button>
                <Button marginTop="1rem" marginLeft="0.5rem" size="sm" colorScheme="gray2" onClick={() => reset()}>
                    {capitalize(t("common:form-reset"))}
                </Button>
            </form>
        </Box>
    )
}

const mapStateToProps = (state: AppState) => ({
    updateResult: state.users.updateUserDataStatus,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    resetUpdateStatus: () => dispatch(resetUpdateUserDataStatusAction()),
    updateProfile: (data: { nickName?: string; language?: string }) => dispatch(updateUserDataAction.started(data)),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(UpdateProfileForm))
