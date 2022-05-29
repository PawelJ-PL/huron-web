import { Button } from "@chakra-ui/button"
import { FormControl, FormErrorMessage, FormLabel } from "@chakra-ui/form-control"
import { Input } from "@chakra-ui/input"
import { zodResolver } from "@hookform/resolvers/zod"
import { capitalize } from "lodash"
import React from "react"
import { useForm } from "react-hook-form"
import { WithTranslation, withTranslation } from "react-i18next"
import { z } from "zod"
import { passwordSchema } from "../types/fieldSchemas"

type Props = {
    onSubmit: (formData: { password: string; email: string }) => void
    submitInProgress: boolean
} & Pick<WithTranslation, "t">

const ResetPasswordForm: React.FC<Props> = ({ onSubmit, submitInProgress, t }) => {
    const formSchema = z
        .object({
            password: passwordSchema(t),
            passwordConfirmation: passwordSchema(t),
            email: z.string().min(1, { message: t("common:field-required") }),
        })
        .refine(({ password, passwordConfirmation }) => password === passwordConfirmation, {
            message: t("reset-password-page:not-the-same-passwords"),
            path: ["passwordConfirmation"],
        })

    type FormData = z.infer<typeof formSchema>

    const {
        register,
        handleSubmit,
        formState: { errors, isValidating },
    } = useForm<FormData>({ resolver: zodResolver(formSchema), shouldUnregister: true })

    return (
        <form onSubmit={handleSubmit(onSubmit)}>
            <FormControl id="email" marginTop="0.5rem" isInvalid={errors.email !== undefined} isRequired={true}>
                <FormLabel fontSize="sm">{capitalize(t("common:email"))}</FormLabel>
                <Input type="email" placeholder={capitalize(t("common:email"))} {...register("email")} />
                <FormErrorMessage>{errors.email?.message}</FormErrorMessage>
            </FormControl>

            <FormControl id="password" marginTop="0.5rem" isInvalid={errors.password !== undefined} isRequired={true}>
                <FormLabel fontSize="sm">{capitalize(t("common:password"))}</FormLabel>
                <Input type="password" placeholder={capitalize(t("common:password"))} {...register("password")} />
                <FormErrorMessage>{errors.password?.message}</FormErrorMessage>
            </FormControl>

            <FormControl
                id="password-confirmation"
                marginTop="0.5rem"
                isInvalid={errors.passwordConfirmation !== undefined}
                isRequired={true}
            >
                <FormLabel fontSize="sm">{capitalize(t("reset-password-page:confirm-password"))}</FormLabel>
                <Input
                    type="password"
                    placeholder={capitalize(t("reset-password-page:confirm-password"))}
                    {...register("passwordConfirmation")}
                />
                <FormErrorMessage>{errors.passwordConfirmation?.message}</FormErrorMessage>
            </FormControl>

            <Button
                marginTop="1rem"
                colorScheme="brand"
                width="full"
                loadingText={t("reset-password-page:set-new-password")}
                isLoading={isValidating || submitInProgress}
                type="submit"
            >
                {t("reset-password-page:set-new-password")}
            </Button>
        </form>
    )
}

export default withTranslation()(ResetPasswordForm)
