import { Button } from "@chakra-ui/button"
import { FormControl, FormErrorMessage } from "@chakra-ui/form-control"
import { InputGroup, InputLeftElement, Input } from "@chakra-ui/input"
import { Box } from "@chakra-ui/layout"
import { zodResolver } from "@hookform/resolvers/zod"
import capitalize from "lodash/capitalize"
import React from "react"
import { useForm } from "react-hook-form"
import { WithTranslation, withTranslation } from "react-i18next"
import { FaAt } from "react-icons/fa"
import { z } from "zod"

type Props = {
    onSubmit: (formData: { email: string }) => void
    submitInProgress: boolean
} & Pick<WithTranslation, "t">

const RequestPasswordResetForm: React.FC<Props> = ({ t, onSubmit, submitInProgress }) => {
    const formSchema = z.object({
        email: z.string().min(1, { message: t("common:field-required") }),
    })

    type FormData = z.infer<typeof formSchema>

    const {
        register,
        handleSubmit,
        formState: { errors, isValidating },
    } = useForm<FormData>({ resolver: zodResolver(formSchema), shouldUnregister: true })

    return (
        <Box>
            <form onSubmit={handleSubmit(onSubmit)}>
                <FormControl id="email" marginTop="1rem" isInvalid={errors.email !== undefined}>
                    <InputGroup marginTop="1rem">
                        <InputLeftElement pointerEvents="none" color="brand.200">
                            <FaAt />
                        </InputLeftElement>
                        <Input type="email" placeholder={capitalize(t("common:email"))} {...register("email")} />
                    </InputGroup>
                    <FormErrorMessage>{errors.email?.message}</FormErrorMessage>
                </FormControl>
                <Button
                    type="submit"
                    marginTop="1rem"
                    isFullWidth={true}
                    colorScheme="brand"
                    loadingText={t("password-reset-request-page:send-link")}
                    isLoading={isValidating || submitInProgress}
                    size="sm"
                    whiteSpace="break-spaces"
                >
                    {t("password-reset-request-page:send-link")}
                </Button>
            </form>
        </Box>
    )
}

export default withTranslation()(RequestPasswordResetForm)
