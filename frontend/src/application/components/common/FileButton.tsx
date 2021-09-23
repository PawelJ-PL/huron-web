import { Button, ButtonProps } from "@chakra-ui/button"
import React, { ChangeEvent } from "react"

type Props = {
    text: string
    buttonProps?: Omit<ButtonProps, "onClick">
    onLoad: (file: File) => void
}

const FileButton: React.FC<Props> = ({ text, buttonProps, onLoad }) => {
    const hiddenInputRef = React.useRef<HTMLInputElement | null>(null)

    const onChange = (event: ChangeEvent<HTMLInputElement>) => {
        const filesList = event.target.files
        return filesList === null ? void 0 : onLoad(filesList[0])
    }

    return (
        <>
            <input type="file" ref={hiddenInputRef} style={{ display: "none" }} onChange={onChange} multiple={false} />
            <Button {...(buttonProps ?? {})} onClick={() => hiddenInputRef?.current?.click()}>
                {text}
            </Button>
        </>
    )
}

export default FileButton
