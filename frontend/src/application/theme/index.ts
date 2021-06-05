import { styles } from "./styles"
import { colors } from "./colors"
import { extendTheme } from "@chakra-ui/react"
import { link } from "./components/link"
import { button } from "./components/button"
import { iconButton } from "./components/icon_button"

const overrides = {
    colors,
    styles,
    components: {
        Link: link,
        IconButton: iconButton,
        Button: button,
    },
}

export const theme = extendTheme(overrides)
