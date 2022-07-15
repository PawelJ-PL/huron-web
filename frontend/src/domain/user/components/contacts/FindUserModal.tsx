import {
    Avatar,
    Box,
    Flex,
    HStack,
    Input,
    Modal,
    ModalBody,
    ModalCloseButton,
    ModalContent,
    ModalHeader,
    ModalOverlay,
    Text,
} from "@chakra-ui/react"
import React, { useCallback, useState } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import debounce from "lodash/debounce"
import { useEffect } from "react"
import { AppState } from "../../../../application/store"
import { Dispatch } from "redux"
import { findUsersAction, resetFindUsersResultAction } from "../../store/Actions"
import { connect } from "react-redux"
import { UserPublicData } from "../../types/UserPublicData"
import { svgContentToUrl } from "../../../../application/utils/image"
import { toSvg } from "jdenticon"
import { FIND_USERS_INPUT } from "./testids"

type Props = {
    isOpen: boolean
    onClose: () => void
    onSelect: (user: UserPublicData) => void
    excludeContacts: boolean
} & Pick<WithTranslation, "t"> &
    ReturnType<typeof mapStateToProps> &
    ReturnType<typeof mapDispatchToProps>

export const FindUserModal: React.FC<Props> = ({
    t,
    isOpen,
    onClose,
    findUsers,
    resetFindResult,
    matchingUsers,
    onSelect,
    excludeContacts,
}) => {
    const initialFocusRef = React.useRef<HTMLInputElement | null>(null)

    const [inputValue, setInputValue] = useState<string>("")

    const handleClose = () => {
        resetFindResult()
        setInputValue("")
        onClose()
    }

    // eslint-disable-next-line react-hooks/exhaustive-deps
    const delayedFetchOrReset = useCallback(
        debounce(
            (value: string) =>
                value.trim().length >= 5 ? findUsers(value.trim(), excludeContacts) : resetFindResult(),
            500
        ),
        [findUsers, resetFindResult]
    )

    useEffect(() => {
        delayedFetchOrReset(inputValue)
    }, [inputValue, delayedFetchOrReset])

    const renderAutocomplete = (users: UserPublicData[]) => (
        <Flex direction="column" marginTop="1px" borderWidth="1px" borderRadius="md">
            {users.map((user) => (
                <Box
                    _hover={{ background: "brand.500", color: "white", borderRadius: "md" }}
                    cursor="pointer"
                    key={user.userId}
                    padding="0.5rem"
                    onClick={() => {
                        handleClose()
                        onSelect(user)
                    }}
                >
                    <HStack>
                        <Avatar src={svgContentToUrl(toSvg(user.userId, 100))} size="xs" />
                        <Text>{user.nickName}</Text>
                    </HStack>
                </Box>
            ))}
        </Flex>
    )

    return (
        <Modal isOpen={isOpen} onClose={handleClose} initialFocusRef={initialFocusRef}>
            <ModalOverlay />
            <ModalContent>
                <ModalHeader>{t("find-user-modal:header")}</ModalHeader>
                <ModalCloseButton />
                <ModalBody>
                    <Text>{t("find-user-modal:start-typing")}</Text>
                    <Input
                        ref={initialFocusRef}
                        onChange={(e) => setInputValue(e.target.value)}
                        value={inputValue}
                        data-testid={FIND_USERS_INPUT}
                    />
                    {matchingUsers.length > 0 && renderAutocomplete(matchingUsers)}
                </ModalBody>
            </ModalContent>
        </Modal>
    )
}

const mapStateToProps = (state: AppState) => ({
    matchingUsers: state.users.findUsersResult.status === "FINISHED" ? state.users.findUsersResult.data.result : [],
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    findUsers: (nickNameStart: string, excludeContacts: boolean) =>
        dispatch(findUsersAction.started({ nickNameStart, includeSelf: false, excludeContacts })),
    resetFindResult: () => dispatch(resetFindUsersResultAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(FindUserModal))
