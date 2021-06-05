import { fireEvent, render, screen } from "@testing-library/react"
import React from "react"
import { i18nMock, tFunctionMock } from "../../../../testutils/mocks/i18n-mock"
import { ApiKeyExpirationPicker } from "./ApiKeyExpirationPicker"
import { API_KEY_EXPIRATION_PICKER_INPUT, API_KEY_EXPIRATION_SWITCH } from "./testids"

describe("Api key expiration picker", () => {
    beforeEach(() => jest.useFakeTimers("modern").setSystemTime(new Date("2021-01-01T00:00:00.000Z")))

    afterAll(() => jest.useRealTimers())

    it("should turn expiration on and off", async () => {
        const onChange = jest.fn()
        render(
            <ApiKeyExpirationPicker
                onChange={onChange}
                t={tFunctionMock}
                i18n={i18nMock()}
                defaultValidTo={undefined}
            />
        )
        const expirationSwitch = screen.getByTestId(API_KEY_EXPIRATION_SWITCH)
        fireEvent.click(expirationSwitch)
        fireEvent.click(expirationSwitch)
        expect(onChange).toHaveBeenCalledTimes(3)
        expect(onChange).toHaveBeenNthCalledWith(1, null)
        expect(onChange).toHaveBeenNthCalledWith(2, new Date("2021-01-01T00:00:00.000Z"))
        expect(onChange).toHaveBeenNthCalledWith(3, null)
    })

    it("should set date", async () => {
        const onChange = jest.fn()
        render(
            <ApiKeyExpirationPicker
                onChange={onChange}
                t={tFunctionMock}
                i18n={i18nMock()}
                defaultValidTo={undefined}
            />
        )
        const expirationSwitch = screen.getByTestId(API_KEY_EXPIRATION_SWITCH)
        fireEvent.click(expirationSwitch)
        const dateInput = screen.getByTestId(API_KEY_EXPIRATION_PICKER_INPUT)
        fireEvent.click(dateInput)
        const day = await screen.findByText("17")
        fireEvent.click(day)
        const hour = screen.getByText("14:00")
        fireEvent.click(hour)
        expect(onChange).toHaveBeenCalledTimes(4)
        expect(onChange).toHaveBeenNthCalledWith(1, null)
        expect(onChange).toHaveBeenNthCalledWith(2, new Date("2021-01-01T00:00:00.000Z"))
        expect(onChange).toHaveBeenNthCalledWith(3, new Date("2021-01-17T00:00:00.000Z"))
        expect(onChange).toHaveBeenNthCalledWith(4, new Date("2021-01-17T14:00:00.000Z"))
    })

    it("should unset date", async () => {
        const onChange = jest.fn()
        render(
            <ApiKeyExpirationPicker
                onChange={onChange}
                t={tFunctionMock}
                i18n={i18nMock()}
                defaultValidTo={"2021-02-01T00:00:00.000Z"}
            />
        )
        const expirationSwitch = screen.getByTestId(API_KEY_EXPIRATION_SWITCH)
        fireEvent.click(expirationSwitch)
        expect(onChange).toHaveBeenCalledTimes(2)
        expect(onChange).toHaveBeenNthCalledWith(1, new Date("2021-02-01T00:00:00.000Z"))
        expect(onChange).toHaveBeenNthCalledWith(2, null)
    })
})
