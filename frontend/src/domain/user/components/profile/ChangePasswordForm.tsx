import { FormControl, FormErrorMessage, FormLabel } from "@chakra-ui/form-control"
import { Input } from "@chakra-ui/input"
import { Box, Stack } from "@chakra-ui/layout"
import { zodResolver } from "@hookform/resolvers/zod"
import React, { useEffect } from "react"
import { useForm } from "react-hook-form"
import { withTranslation, WithTranslation } from "react-i18next"
import { z } from "zod"
import { passwordSchema } from "../../types/fieldSchemas"
import capitalize from "lodash/capitalize"
import { Button } from "@chakra-ui/button"
import { AppState } from "../../../../application/store"
import { Dispatch } from "redux"
import { ChangePasswordData } from "../../api/UsersApi"
import { changePasswordAction, resetChangePasswordStatusAction } from "../../store/Actions"
import { connect } from "react-redux"
import { InvalidCredentials, InvalidEmail } from "../../api/errors"
import AlertBox from "../../../../application/components/common/AlertBox"

type Props = Pick<WithTranslation, "t"> & ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps>

const ChangePasswordForm: React.FC<Props> = ({ t, changePasswordResult, resetResult, changePassword }) => {
    useEffect(() => {
        resetResult()
        return () => {
            resetResult()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const formSchema = z
        .object({
            email: z.string().min(1, { message: t("common:field-required") }),
            oldPassword: passwordSchema(t),
            newPassword: passwordSchema(t),
            confirmNewPassword: z.string(),
        })
        .refine(({ newPassword, confirmNewPassword }) => newPassword === confirmNewPassword, {
            message: t("profile-page:password-change.not-the-same-passwords"),
            path: ["confirmNewPassword"],
        })
        .refine(({ newPassword, oldPassword }) => newPassword !== oldPassword, {
            message: t("profile-page:password-change.old-and-new-are-equal"),
            path: ["newPassword"],
        })

    type FormData = z.infer<typeof formSchema>

    const onSubmit = (data: FormData) =>
        changePassword({ email: data.email, currentPassword: data.oldPassword, newPassword: data.newPassword })

    const {
        register,
        handleSubmit,
        formState: { errors, isValidating },
        reset,
    } = useForm<FormData>({ resolver: zodResolver(formSchema), shouldUnregister: true })

    useEffect(() => {
        if (changePasswordResult.status === "FINISHED") {
            reset()
        }
    }, [changePasswordResult, reset])

    const renderError = (error: Error) => {
        let status: "error" | "warning"
        let title: string

        if (error instanceof InvalidCredentials) {
            status = "warning"
            title = t("profile-page:password-change.invalid-credentials-error")
        } else if (error instanceof InvalidEmail) {
            status = "warning"
            title = t("profile-page:password-change.invalid-email-error")
        } else {
            status = "error"
            title = capitalize(t("common:unexpected-error"))
        }

        return (
            <AlertBox
                status={status}
                icon={true}
                title={title}
                onClose={resetResult}
                alertProps={{ marginBottom: "0.5rem" }}
            />
        )
    }

    return (
        <Box>
            {changePasswordResult.status === "FAILED" && renderError(changePasswordResult.error)}
            {changePasswordResult.status === "FINISHED" && (
                <AlertBox
                    status="success"
                    icon={true}
                    title={t("profile-page:password-change.password-changed-message")}
                    onClose={resetResult}
                    alertProps={{ marginBottom: "0.5rem" }}
                />
            )}
            <form onSubmit={handleSubmit(onSubmit)}>
                <Stack spacing="0.7rem" direction={["column", null, null, "row"]}>
                    <FormControl id="email" isRequired={true} isInvalid={errors.email !== undefined}>
                        <FormLabel fontSize="sm">{capitalize(t("common:email"))}</FormLabel>
                        <Input type="email" placeholder={capitalize(t("common:email"))} {...register("email")} />
                        <FormErrorMessage>{errors.email?.message}</FormErrorMessage>
                    </FormControl>
                    <FormControl id="oldPassword" isRequired={true} isInvalid={errors.oldPassword !== undefined}>
                        <FormLabel fontSize="sm">
                            {capitalize(t("profile-page:password-change.old-password-field"))}
                        </FormLabel>
                        <Input
                            type="password"
                            placeholder={capitalize(t("profile-page:password-change.old-password-field"))}
                            {...register("oldPassword")}
                        />
                        <FormErrorMessage>{errors.oldPassword?.message}</FormErrorMessage>
                    </FormControl>
                </Stack>
                <Stack
                    spacing="0.7rem"
                    direction={["column", null, null, "row"]}
                    marginTop={["0.7rem", null, null, "1.5rem"]}
                >
                    <FormControl id="newPassword" isRequired={true} isInvalid={errors.newPassword !== undefined}>
                        <FormLabel fontSize="sm">
                            {capitalize(t("profile-page:password-change.new-password-field"))}
                        </FormLabel>
                        <Input
                            type="password"
                            placeholder={capitalize(t("profile-page:password-change.new-password-field"))}
                            {...register("newPassword")}
                        />
                        <FormErrorMessage>{errors.newPassword?.message}</FormErrorMessage>
                    </FormControl>
                    <FormControl
                        id="confirmNewPassword"
                        isRequired={true}
                        isInvalid={errors.confirmNewPassword !== undefined}
                    >
                        <FormLabel fontSize="sm">
                            {capitalize(t("profile-page:password-change.confirm-new-password-field"))}
                        </FormLabel>
                        <Input
                            type="password"
                            placeholder={capitalize(t("profile-page:password-change.confirm-new-password-field"))}
                            {...register("confirmNewPassword")}
                        />
                        <FormErrorMessage>{errors.confirmNewPassword?.message}</FormErrorMessage>
                    </FormControl>
                </Stack>
                <Button
                    marginTop="1rem"
                    colorScheme="brand"
                    loadingText={capitalize(t("profile-page:password-change.submit-button"))}
                    isLoading={isValidating || changePasswordResult.status === "PENDING"}
                    type="submit"
                    size="sm"
                >
                    {capitalize(t("profile-page:password-change.submit-button"))}
                </Button>
            </form>
        </Box>
    )
}

const mapStateToProps = (state: AppState) => ({
    changePasswordResult: state.users.changePassword,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    changePassword: (data: ChangePasswordData) => dispatch(changePasswordAction.started(data)),
    resetResult: () => dispatch(resetChangePasswordStatusAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(ChangePasswordForm))
