import { FormControl, FormLabel } from "@chakra-ui/form-control"
import { Input } from "@chakra-ui/input"
import { Box, BoxProps } from "@chakra-ui/layout"
import { Switch } from "@chakra-ui/switch"
import React, { useEffect, useState } from "react"
import { withTranslation, WithTranslation } from "react-i18next"
import { dateLocaleForLang } from "../../../../application/localization/utils"
import DatePicker from "react-datepicker"
import { API_KEY_EXPIRATION_PICKER_INPUT, API_KEY_EXPIRATION_SWITCH } from "./testids"

function extractDate(newDate: Date | [Date | null, Date | null] | null): Date {
    if (newDate === null) {
        return new Date()
    } else if (newDate instanceof Date) {
        return newDate
    } else {
        return newDate[0] ?? new Date()
    }
}

type Props = {
    defaultValidTo?: string
    containerProps?: BoxProps
    onChange: (d: Date | null) => void
} & Pick<WithTranslation, "t" | "i18n">

export const ApiKeyExpirationPicker: React.FC<Props> = ({ t, i18n, defaultValidTo, containerProps, onChange }) => {
    const [expires, setExpires] = useState(Boolean(defaultValidTo))
    const [selectedDate, setSelectedDate] = useState(defaultValidTo ? new Date(defaultValidTo) : new Date())

    useEffect(() => {
        onChange(expires ? selectedDate : null)
    }, [expires, selectedDate, onChange])

    return (
        <Box {...containerProps}>
            <FormControl display="flex" alignItems="center">
                <FormLabel htmlFor="expiration-switch">
                    {t("profile-page:api-key-expirataion-modal.expiration-switch-label")}
                </FormLabel>
                <Switch
                    id="expiration-switch"
                    colorScheme="brand"
                    isChecked={expires}
                    onChange={(value) => setExpires(value.target.checked)}
                    data-testid={API_KEY_EXPIRATION_SWITCH}
                />
            </FormControl>

            {expires && (
                <Box>
                    <DatePicker
                        customInput={<Input size="sm" data-testid={API_KEY_EXPIRATION_PICKER_INPUT} />}
                        locale={dateLocaleForLang(i18n.languages[0])}
                        selected={selectedDate}
                        showTimeSelect={true}
                        dateFormat="Pp"
                        onChange={(date) => setSelectedDate(extractDate(date))}
                        timeCaption={t("profile-page:api-key-expirataion-modal.datepicker-time-caption")}
                        timeIntervals={60}
                    />
                </Box>
            )}
        </Box>
    )
}

export default withTranslation()(ApiKeyExpirationPicker)
