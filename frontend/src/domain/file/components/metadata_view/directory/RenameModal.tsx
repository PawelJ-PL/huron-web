import { Button } from "@chakra-ui/button"
import { FormControl, FormErrorMessage, FormLabel } from "@chakra-ui/form-control"
import { Input } from "@chakra-ui/input"
import { Modal, ModalBody, ModalContent, ModalFooter, ModalOverlay } from "@chakra-ui/react"
import { zodResolver } from "@hookform/resolvers/zod"
import React, { useCallback, useEffect } from "react"
import { useForm } from "react-hook-form"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import { z } from "zod"
import { filenameSchema } from "../../../types/fieldSchemas"
import capitalize from "lodash/capitalize"
import { AppState } from "../../../../../application/store"
import { renameFileAction, renameRequestAction } from "../../../store/Actions"
import { FilesystemUnitMetadata } from "../../../types/FilesystemUnitMetadata"
import { NEW_FILE_NAME_INPUT, RENAME_FILE_MODAL } from "../../testids"

type Props = Pick<WithTranslation, "t"> & ReturnType<typeof mapDispatchToProps> & ReturnType<typeof mapStateToProps>

export const RenameModal: React.FC<Props> = ({ t, requestedFile, clearRequest, rename }) => {
    const formSchema = z.object({
        name: filenameSchema(t),
    })

    type FormData = z.infer<typeof formSchema>

    const onSubmit = (data: FormData) => {
        clearRequest()
        if (data.name === requestedFile?.name) {
            return
        }
        if (requestedFile) {
            rename(requestedFile, data.name)
        }
    }

    const {
        register,
        setValue,
        handleSubmit,
        formState: { errors, isValidating },
    } = useForm<FormData>({
        resolver: zodResolver(formSchema),
        shouldUnregister: true,
        defaultValues: { name: "" },
    })

    const setName = useCallback(
        (name: string) => {
            setValue("name", name)
        },
        [setValue]
    )

    useEffect(() => {
        setName(requestedFile?.name ?? "")
    }, [requestedFile, setName])

    return (
        <Modal isOpen={requestedFile !== null} onClose={clearRequest}>
            <ModalOverlay />
            <ModalContent data-testid={RENAME_FILE_MODAL}>
                <ModalBody>
                    <form onSubmit={handleSubmit(onSubmit)} id="rename-form">
                        <FormControl id="description" isInvalid={errors.name !== undefined} isRequired={true}>
                            <FormLabel fontSize="sm">{t("file-view:directory-content-list.new-name-label")}</FormLabel>
                            <Input {...register("name")} data-testid={NEW_FILE_NAME_INPUT} />
                            <FormErrorMessage>{errors.name?.message}</FormErrorMessage>
                        </FormControl>
                    </form>
                </ModalBody>

                <ModalFooter>
                    <Button size="xs" onClick={clearRequest} marginRight="0.2em" colorScheme="gray2">
                        {capitalize(t("common:cancel-imperative"))}
                    </Button>
                    <Button
                        type="submit"
                        form="rename-form"
                        size="xs"
                        marginLeft="0.2em"
                        colorScheme="brand"
                        loadingText={capitalize(t("common:create-imperative"))}
                        isLoading={isValidating}
                    >
                        {capitalize(t("common:confirm-imperative"))}
                    </Button>
                </ModalFooter>
            </ModalContent>
        </Modal>
    )
}

const mapStateToProps = (state: AppState) => ({
    requestedFile: state.files.renameRequest,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    clearRequest: () => dispatch(renameRequestAction(null)),
    rename: (file: FilesystemUnitMetadata, newName: string) =>
        dispatch(renameFileAction.started({ collectionId: file.collectionId, fileId: file.id, newName })),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(RenameModal))
