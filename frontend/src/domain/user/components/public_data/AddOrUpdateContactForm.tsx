import React from "react"
import { nicknameSchema } from "../../types/fieldSchemas"
import { z } from "zod"
import { WithTranslation, withTranslation } from "react-i18next"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Box, BoxProps, FormControl, FormErrorMessage, FormLabel, Input } from "@chakra-ui/react"
import capitalize from "lodash/capitalize"

type FormData = { alias?: string }

type Props = {
    onSubmit: (data: FormData) => void
    defaultValues: { alias?: string }
    containerProps?: BoxProps
} & Pick<WithTranslation, "t">

const AddOrUpdateContactForm: React.FC<Props> = ({ t, onSubmit, containerProps, defaultValues }) => {
    const formSchema = z.object({
        alias: nicknameSchema(t).optional(),
    })

    const {
        register,
        handleSubmit,
        formState: { errors },
    } = useForm<FormData>({
        resolver: zodResolver(formSchema),
        shouldUnregister: true,
        defaultValues: { alias: defaultValues.alias },
    })

    return (
        <Box {...(containerProps ?? {})}>
            <form onSubmit={handleSubmit(onSubmit)} id="add-contact-form">
                <FormControl id="alias" isInvalid={errors.alias !== undefined}>
                    <FormLabel fontSize="sm">{capitalize(t("common:alias"))}</FormLabel>
                    <Input placeholder={capitalize(t("common:alias"))} {...register("alias")} />
                    <FormErrorMessage>{errors.alias?.message}</FormErrorMessage>
                </FormControl>
            </form>
        </Box>
    )
}

export default withTranslation()(AddOrUpdateContactForm)
