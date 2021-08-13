import { Button } from "@chakra-ui/button"
import { FormControl, FormErrorMessage } from "@chakra-ui/form-control"
import { Input } from "@chakra-ui/input"
import { Modal, ModalBody, ModalContent, ModalFooter, ModalHeader, ModalOverlay } from "@chakra-ui/modal"
import { zodResolver } from "@hookform/resolvers/zod"
import capitalize from "lodash/capitalize"
import React from "react"
import { useForm } from "react-hook-form"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import { z } from "zod"
import { AppState } from "../../../application/store"
import { createCollectionAction } from "../store/Actions"
import { collectionNameSchema } from "../types/fieldSchemas"

type Props = Pick<WithTranslation, "t"> & ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps>

const AutoCreateCollectionModal: React.FC<Props> = ({ t, createCollection, createResult }) => {
    const formSchema = z.object({
        collectionName: collectionNameSchema(t),
    })

    type FormData = z.infer<typeof formSchema>

    const onSubmit = (data: FormData) => createCollection(data.collectionName)

    const {
        register,
        handleSubmit,
        formState: { errors, isValidating },
    } = useForm<FormData>({ resolver: zodResolver(formSchema), shouldUnregister: true })

    return (
        <Modal isOpen={true} onClose={() => void 0}>
            <ModalOverlay />
            <ModalContent>
                <ModalHeader>{t("collections-view:auto-create-collection-modal:header")}</ModalHeader>
                <ModalBody>
                    <form id="createCollectionForm" onSubmit={handleSubmit(onSubmit)}>
                        <FormControl
                            id="collectionName"
                            isRequired={true}
                            isInvalid={errors.collectionName !== undefined}
                        >
                            <Input
                                placeholder={t(
                                    "collections-view:auto-create-collection-modal.collection-name-input-field"
                                )}
                                {...register("collectionName")}
                            />
                            <FormErrorMessage>{errors.collectionName?.message}</FormErrorMessage>
                        </FormControl>
                    </form>
                </ModalBody>
                <ModalFooter>
                    <Button
                        type="submit"
                        form="createCollectionForm"
                        colorScheme="brand"
                        isDisabled={isValidating || createResult.status === "PENDING"}
                        isLoading={isValidating || createResult.status === "PENDING"}
                    >
                        {capitalize(t("common:create-imperative"))}
                    </Button>
                </ModalFooter>
            </ModalContent>
        </Modal>
    )
}

const mapStateToProps = (state: AppState) => ({
    createResult: state.collections.createCollectionResult,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    createCollection: (name: string) => dispatch(createCollectionAction.started(name)),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(AutoCreateCollectionModal))
