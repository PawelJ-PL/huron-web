import {
    Button,
    ButtonGroup,
    Checkbox,
    Divider,
    Heading,
    Modal,
    ModalBody,
    ModalContent,
    ModalFooter,
    ModalHeader,
    ModalOverlay,
    Stack,
} from "@chakra-ui/react"
import capitalize from "lodash/capitalize"
import kebabCase from "lodash/kebabCase"
import React, { useState } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import { AppState } from "../../../../../application/store"
import { UserPublicData } from "../../../../user/types/UserPublicData"
import { addMemberAction, AddMemberParams, setMemberPermissionsAction } from "../../../store/Actions"
import { Collection } from "../../../types/Collection"
import { CollectionPermission, collectionPermissionSchema } from "../../../types/CollectionPermission"

type AddMemberProps = { masterKey: string }

type UpdateMemberProps = { currentPermissions: CollectionPermission[] }

type Props = {
    isOpen: boolean
    onClose: () => void
    collection: Collection
    user: UserPublicData
    operationProps: AddMemberProps | UpdateMemberProps
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps>

const REQUIRE_FILE_METADATA: CollectionPermission[] = ["CreateFile", "ModifyFile", "ReadFile"]

const AddOrUpdateMemberModal: React.FC<Props> = ({
    isOpen,
    onClose,
    collection,
    user,
    operationProps,
    actionInProgress,
    addMember,
    updatePermissions,
    t,
}) => {
    const handleClose = () => {
        onClose()
    }

    const handleConfirmation = () => {
        if ("masterKey" in operationProps) {
            addMember({
                collectionId: collection.id,
                userId: user.userId,
                permissions: newPermissions,
                masterKey: operationProps.masterKey,
            })
        } else if ("currentPermissions" in operationProps) {
            updatePermissions(collection.id, user.userId, newPermissions)
        }
        onClose()
    }

    const [newPermissions, setNewPermissions] = useState<CollectionPermission[]>(
        "currentPermissions" in operationProps ? operationProps.currentPermissions : ["ReadFileMetadata"]
    )

    const onPermissionChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const value = event.target.value
        const parseResult = collectionPermissionSchema.safeParse(value)
        if (!parseResult.success) {
            return
        }
        const permission = parseResult.data
        const checked = event.target.checked
        if (!checked) {
            setNewPermissions((prev) => prev.filter((p) => p !== permission))
        } else {
            const updateWith: CollectionPermission[] = REQUIRE_FILE_METADATA.includes(permission)
                ? [permission, "ReadFileMetadata"]
                : [permission]
            setNewPermissions((prev) => Array.from(new Set(prev.concat(updateWith))))
        }
    }

    const renderCheckboxFor = (permission: CollectionPermission) => (
        <Checkbox
            key={permission}
            size="sm"
            colorScheme="brand"
            onChange={onPermissionChange}
            value={permission}
            isChecked={newPermissions.includes(permission)}
            isDisabled={
                permission === "ReadFileMetadata" && newPermissions.some((p) => REQUIRE_FILE_METADATA.includes(p))
            }
        >
            {t(`collection-permission:${kebabCase(permission)}`)}
        </Checkbox>
    )

    return (
        <Modal isOpen={isOpen} onClose={handleClose}>
            <ModalOverlay />
            <ModalContent>
                <ModalHeader>
                    {t("collection-manage-page:set-permissions", { collectionName: collection.name })}
                </ModalHeader>
                <ModalBody>
                    <Heading as="h5" size="sm">
                        {user.contactData?.alias ?? user.nickName}
                    </Heading>
                    <Divider />
                    <Stack>{collectionPermissionSchema.options.map((p) => renderCheckboxFor(p))}</Stack>
                </ModalBody>
                <ModalFooter>
                    <ButtonGroup size="xs">
                        <Button onClick={handleClose} colorScheme="gray2">
                            {capitalize(t("common:cancel-imperative"))}
                        </Button>
                        <Button
                            colorScheme="brand"
                            onClick={handleConfirmation}
                            isDisabled={newPermissions.length < 1}
                            loadingText={t("common:confirm-imperative")}
                            isLoading={actionInProgress}
                        >
                            {t("common:confirm-imperative")}
                        </Button>
                    </ButtonGroup>
                </ModalFooter>
            </ModalContent>
        </Modal>
    )
}

const mapStateToProps = (state: AppState) => ({
    actionInProgress:
        state.collections.addMember.status === "PENDING" || state.collections.setPermissions.status === "PENDING",
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    addMember: (params: AddMemberParams) => dispatch(addMemberAction.started(params)),
    updatePermissions: (collectionId: string, memberId: string, permissions: CollectionPermission[]) =>
        dispatch(setMemberPermissionsAction.started({ collectionId, memberId, permissions })),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(AddOrUpdateMemberModal))
