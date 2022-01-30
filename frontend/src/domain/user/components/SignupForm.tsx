import { Button, IconButton } from "@chakra-ui/button"
import { FormControl, FormErrorMessage, FormLabel } from "@chakra-ui/form-control"
import { Input, InputGroup, InputRightElement } from "@chakra-ui/input"
import { Box } from "@chakra-ui/layout"
import { zodResolver } from "@hookform/resolvers/zod"
import capitalize from "lodash/capitalize"
import React, { useState } from "react"
import { useForm } from "react-hook-form"
import { WithTranslation, withTranslation } from "react-i18next"
import { z } from "zod"
import { ImEye } from "react-icons/im"
import { ImEyeBlocked } from "react-icons/im"
import { nicknameSchema, passwordSchema } from "../types/fieldSchemas"

type Props = {
    onSubmit: (formData: { nickname: string; email: string; password: string }) => void
    submitInProgress: boolean
} & WithTranslation

const SignupForm: React.FC<Props> = ({ t, onSubmit, submitInProgress }) => {
    const [passwordShown, setPasswordShown] = useState<boolean>(false)

    const formSchema = z.object({
        nickname: nicknameSchema(t),
        email: z.string().email({ message: t("signup-page:invalid-email-format") }),
        password: passwordSchema(t),
    })

    type FormData = z.infer<typeof formSchema>

    const {
        register,
        handleSubmit,
        formState: { errors, isValidating },
    } = useForm<FormData>({ resolver: zodResolver(formSchema), shouldUnregister: true })

    const passwordButtonAttributes = passwordShown
        ? { "aria-label": t("signup-page:hide-password-button-label"), icon: <ImEyeBlocked /> }
        : { "aria-label": t("signup-page:show-password-button-label"), icon: <ImEye /> }

    return (
        <Box>
            <form onSubmit={handleSubmit(onSubmit)}>
                <FormControl id="nickname" marginTop="1rem" isRequired={true} isInvalid={errors.nickname !== undefined}>
                    <FormLabel fontSize="sm">{capitalize(t("common:nickname"))}</FormLabel>
                    <Input placeholder={capitalize(t("common:nickname"))} {...register("nickname")} />
                    <FormErrorMessage>{errors.nickname?.message}</FormErrorMessage>
                </FormControl>
                <FormControl marginTop="1rem" isRequired={true} isInvalid={errors.email !== undefined}>
                    <FormLabel htmlFor="email" fontSize="sm">
                        {t("signup-page:email-address-label")}
                    </FormLabel>
                    <Input type="email" placeholder={capitalize(t("common:email"))} {...register("email")} />
                    <FormErrorMessage>{errors.email?.message}</FormErrorMessage>
                </FormControl>
                <FormControl id="password" marginTop="1rem" isRequired={true} isInvalid={errors.password !== undefined}>
                    <FormLabel fontSize="sm">{capitalize(t("common:password"))}</FormLabel>
                    <InputGroup>
                        <Input
                            type={passwordShown ? "text" : "password"}
                            placeholder={capitalize(t("common:password"))}
                            {...register("password")}
                        />
                        <InputRightElement>
                            <IconButton
                                {...passwordButtonAttributes}
                                onClick={() => setPasswordShown((prev) => !prev)}
                                size="s"
                                _focus={{ boxShadow: "none" }}
                            />
                        </InputRightElement>
                    </InputGroup>
                    <FormErrorMessage>{errors.password?.message}</FormErrorMessage>
                </FormControl>
                <Button
                    marginTop="1rem"
                    colorScheme="brand"
                    isFullWidth={true}
                    loadingText={t("signup-page:create-account")}
                    isLoading={isValidating || submitInProgress}
                    type="submit"
                >
                    {t("signup-page:create-account")}
                </Button>
            </form>
        </Box>
    )
}

export default withTranslation()(SignupForm)
