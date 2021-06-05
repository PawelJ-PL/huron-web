import React from "react"
import { z } from "zod"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Box } from "@chakra-ui/layout"
import { FormControl, FormErrorMessage } from "@chakra-ui/form-control"
import { Input, InputGroup, InputLeftElement } from "@chakra-ui/input"
import { Button } from "@chakra-ui/button"
import { FaUserAlt, FaLock } from "react-icons/fa"
import { WithTranslation, withTranslation } from "react-i18next"
import capitalize from "lodash/capitalize"
import { passwordSchema } from "../types/fieldSchemas"

type Props = {
    onSubmit: (formData: { email: string; password: string }) => void
    submitInProgress: boolean
} & WithTranslation

const LoginForm: React.FC<Props> = ({ onSubmit, submitInProgress, t }) => {
    const formSchema = z.object({
        email: z.string().min(1, { message: t("common:field-required") }),
        password: passwordSchema(t),
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
                    <InputGroup>
                        <InputLeftElement pointerEvents="none" color="brand.200">
                            <FaUserAlt />
                        </InputLeftElement>
                        <Input type="email" placeholder={capitalize(t("common:email"))} {...register("email")} />
                    </InputGroup>
                    <FormErrorMessage>{errors.email?.message}</FormErrorMessage>
                </FormControl>
                <FormControl id="password" marginTop="1rem" isInvalid={errors.password !== undefined}>
                    <InputGroup>
                        <InputLeftElement pointerEvents="none" color="brand.200">
                            <FaLock />
                        </InputLeftElement>
                        <Input
                            type="password"
                            placeholder={capitalize(t("common:password"))}
                            {...register("password")}
                        />
                    </InputGroup>
                    <FormErrorMessage>{errors.password?.message}</FormErrorMessage>
                </FormControl>
                <Button
                    marginTop="1rem"
                    colorScheme="brand"
                    isFullWidth={true}
                    loadingText="Login"
                    isLoading={isValidating || submitInProgress}
                    type="submit"
                >
                    Login
                </Button>
            </form>
        </Box>
    )
}

export default withTranslation()(LoginForm)
