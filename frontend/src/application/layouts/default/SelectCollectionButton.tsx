import { IconButton } from "@chakra-ui/button"
import { useDisclosure } from "@chakra-ui/hooks"
import { Box } from "@chakra-ui/layout"
import React from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { ImDrawer } from "react-icons/im"
import FetchCollectionsModalWrapper from "../../../domain/collection/components/FetchCollectionsModalWrapper"

type Props = Pick<WithTranslation, "t">

const SelectCollectionButton: React.FC<Props> = ({ t }) => {
    const { isOpen, onClose, onOpen } = useDisclosure()

    return (
        <Box>
            <FetchCollectionsModalWrapper isOpen={isOpen} onClose={onClose} />
            <IconButton
                aria-label={t("top-bar:select-collection.aria-label")}
                icon={<ImDrawer viewBox="0 2 16 16" size={32} />}
                marginRight={["0.8em", "1.5em"]}
                variant="ghost"
                _hover={{ background: "inherit" }}
                onClick={onOpen}
            />
        </Box>
    )
}

export default withTranslation()(SelectCollectionButton)
