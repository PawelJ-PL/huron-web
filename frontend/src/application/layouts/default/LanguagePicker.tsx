import React from "react"
import { withTranslation, WithTranslation } from "react-i18next"
import { Us } from "react-flags-select"
import { Pl } from "react-flags-select"
import { Box, Flex, Text } from "@chakra-ui/layout"
import { useBreakpointValue } from "@chakra-ui/media-query"
import { Menu, MenuButton, MenuItem, MenuList } from "@chakra-ui/menu"
import { supportedLanguages } from "../../localization/i18n"
import { Dispatch } from "redux"
import { updateUserDataAction } from "../../../domain/user/store/Actions"
import { connect } from "react-redux"
import capitalize from "lodash/capitalize"
import { LANGUAGE_MENU_ITEM_PREFIX, LANGUAGE_SELECTION } from "./testids"

type CurrentIconProps = Pick<WithTranslation, "i18n">
type Props = CurrentIconProps & ReturnType<typeof mapDispatchToProps>

const languageIconMapping: Record<
    string,
    { icon: (props: React.SVGProps<SVGSVGElement>) => JSX.Element; name: string }
> = {
    en: { icon: Us, name: "English" },
    pl: { icon: Pl, name: "Polski" },
}

const CurrentIcon: React.FC<CurrentIconProps> = ({ i18n }) => {
    const showFullLang = useBreakpointValue({ base: false, sm: true })
    const currentLanguage = i18n.languages[0].toLowerCase()
    const shown =
        currentLanguage in languageIconMapping ? languageIconMapping[currentLanguage] : languageIconMapping["en"]
    const Icon = shown.icon

    return (
        <Flex data-testid={LANGUAGE_SELECTION}>
            <Box>
                <Icon />
            </Box>
            {showFullLang && (
                <Text fontSize="sm" marginLeft="0.3em">
                    {shown.name}
                </Text>
            )}
        </Flex>
    )
}

export const LanguagePicker: React.FC<Props> = ({ i18n, updateProfileLanguage }) => {
    const onLanguageSelect = (selectedLang: string) => {
        if (selectedLang !== i18n.languages[0] && supportedLanguages.includes(selectedLang)) {
            i18n.changeLanguage(selectedLang)
            updateProfileLanguage(selectedLang)
        }
    }

    return (
        <Flex>
            <Menu autoSelect={false}>
                <MenuButton>
                    <CurrentIcon i18n={i18n} />
                </MenuButton>
                <MenuList color="gray.500">
                    {Object.entries(languageIconMapping).map(([key, value]) => (
                        <MenuItem
                            key={value.name}
                            icon={<value.icon width="2em" height="2em" />}
                            onClick={() => onLanguageSelect(key)}
                            data-testid={LANGUAGE_MENU_ITEM_PREFIX + "_" + value.name.toUpperCase()}
                        >
                            <Text fontWeight="black">{value.name}</Text>
                        </MenuItem>
                    ))}
                </MenuList>
            </Menu>
        </Flex>
    )
}

const mapDispatchToProps = (dispatch: Dispatch) => ({
    updateProfileLanguage: (newLanguage: string) =>
        dispatch(updateUserDataAction.started({ language: capitalize(newLanguage) })),
})

export default connect(null, mapDispatchToProps)(withTranslation()(LanguagePicker))
