import { Button } from "@chakra-ui/button"
import { FormControl, FormErrorMessage, FormLabel } from "@chakra-ui/form-control"
import { Input } from "@chakra-ui/input"
import { Modal, ModalBody, ModalContent, ModalFooter, ModalOverlay } from "@chakra-ui/modal"
import { zodResolver } from "@hookform/resolvers/zod"
import React from "react"
import { useForm } from "react-hook-form"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import { z } from "zod"
import { createDirectoryAction, NewDirectoryData } from "../../../store/Actions"
import { filenameSchema } from "../../../types/fieldSchemas"
import capitalize from "lodash/capitalize"
import { NEW_DIRECTORY_NAME_INPUT } from "../../testids"

type Props = {
    isOpen: boolean
    onClose: () => void
    parent: string | null
    collectionId: string
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapDispatchToProps>

export const NewDirectoryModal: React.FC<Props> = ({ isOpen, onClose, t, createDirectory, parent, collectionId }) => {
    const formSchema = z.object({
        name: filenameSchema(t),
    })

    type FormData = z.infer<typeof formSchema>

    const onSubmit = (data: FormData) => {
        createDirectory({ collectionId, parent, name: data.name })
        onClose()
    }

    const {
        register,
        handleSubmit,
        formState: { errors, isValidating },
    } = useForm<FormData>({
        resolver: zodResolver(formSchema),
        shouldUnregister: true,
        defaultValues: { name: "" },
    })

    return (
        <Modal isOpen={isOpen} onClose={onClose}>
            <ModalOverlay />
            <ModalContent>
                <ModalBody>
                    <form onSubmit={handleSubmit(onSubmit)} id="new-directory-form">
                        <FormControl id="description" isInvalid={errors.name !== undefined} isRequired={true}>
                            <FormLabel fontSize="sm">
                                {t("file-view:directory-content-list.new-directory-name")}
                            </FormLabel>
                            <Input {...register("name")} data-testid={NEW_DIRECTORY_NAME_INPUT} />
                            <FormErrorMessage>{errors.name?.message}</FormErrorMessage>
                        </FormControl>
                    </form>
                </ModalBody>

                <ModalFooter>
                    <Button size="xs" onClick={onClose} marginRight="0.2em" colorScheme="gray2">
                        {capitalize(t("common:cancel-imperative"))}
                    </Button>
                    <Button
                        type="submit"
                        form="new-directory-form"
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

const mapDispatchToProps = (dispatch: Dispatch) => ({
    createDirectory: (data: NewDirectoryData) => dispatch(createDirectoryAction.started(data)),
})

export default connect(null, mapDispatchToProps)(withTranslation()(NewDirectoryModal))
