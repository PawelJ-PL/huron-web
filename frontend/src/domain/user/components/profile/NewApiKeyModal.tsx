import { FormControl, FormErrorMessage, FormLabel } from "@chakra-ui/form-control"
import { Modal, ModalBody, ModalContent, ModalFooter, ModalOverlay } from "@chakra-ui/modal"
import { zodResolver } from "@hookform/resolvers/zod"
import React, { useCallback } from "react"
import { useForm } from "react-hook-form"
import { WithTranslation, withTranslation } from "react-i18next"
import { z } from "zod"
import { dateStringSchema } from "../../../../application/types/form-schemas"
import { apiKeyDescriptionSchema } from "../../types/fieldSchemas"
import capitalize from "lodash/capitalize"
import { Input } from "@chakra-ui/input"
import { Button } from "@chakra-ui/button"
import ApiKeyExpirationPicker from "./ApiKeyExpirationPicker"

type Props = {
    isOpen: boolean
    onClose: () => void
    onConfirm: (d: { description: string; validTo?: string | null }) => void
} & Pick<WithTranslation, "t">

const NewApiKeyModal: React.FC<Props> = ({ isOpen, onClose, t, onConfirm }) => {
    const formSchema = z.object({
        description: apiKeyDescriptionSchema(t),
        validTo: dateStringSchema(t).nullish(),
    })

    type FormData = z.infer<typeof formSchema>

    const onSubmit = (data: FormData) => {
        onClose()
        onConfirm(data)
    }

    const {
        register,
        handleSubmit,
        formState: { errors, isValidating },
        setValue,
    } = useForm<FormData>({
        resolver: zodResolver(formSchema),
        shouldUnregister: true,
        defaultValues: { description: "", validTo: undefined },
    })

    const onDateChange = useCallback(
        (d: Date | null) => {
            setValue("validTo", d ? d.toISOString() : undefined)
        },
        [setValue]
    )

    return (
        <Modal isOpen={isOpen} onClose={onClose}>
            <ModalOverlay />
            <ModalContent>
                <ModalBody>
                    <form onSubmit={handleSubmit(onSubmit)} id="new-key-form">
                        <FormControl id="description" isInvalid={errors.description !== undefined} isRequired={true}>
                            <FormLabel fontSize="sm">{capitalize(t("common:description"))}</FormLabel>
                            <Input placeholder={capitalize(t("common:description"))} {...register("description")} />
                            <FormErrorMessage>{errors.description?.message}</FormErrorMessage>

                            <ApiKeyExpirationPicker
                                containerProps={{ marginTop: "0.3em" }}
                                defaultValidTo={undefined}
                                onChange={onDateChange}
                            />
                        </FormControl>
                    </form>
                </ModalBody>

                <ModalFooter>
                    <Button size="xs" onClick={onClose} marginRight="0.2em" colorScheme="gray2">
                        {capitalize(t("common:cancel-imperative"))}
                    </Button>
                    <Button
                        type="submit"
                        form="new-key-form"
                        size="xs"
                        marginLeft="0.2em"
                        colorScheme="brand"
                        loadingText={capitalize(t("common:create-imperative"))}
                        isLoading={isValidating}
                    >
                        {capitalize(t("common:create-imperative"))}
                    </Button>
                </ModalFooter>
            </ModalContent>
        </Modal>
    )
}

export default withTranslation()(NewApiKeyModal)
