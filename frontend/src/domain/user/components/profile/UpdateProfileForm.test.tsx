import { fireEvent, render, screen } from "@testing-library/react"
import React from "react"
import { exampleHashedEmail, exampleUserId, exampleUserNickname } from "../../../../testutils/constants/user"
import { tFunctionMock } from "../../../../testutils/mocks/i18n-mock"
import { UPDATE_PROFILE_LANGUAGE_SELECT } from "./testids"
import { UpdateProfileForm } from "./UpdateProfileForm"

describe("User profile form", () => {
    describe("default values", () => {
        it("should be rendered", () => {
            render(
                <UpdateProfileForm
                    currentData={{
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    }}
                    t={tFunctionMock}
                    updateProfile={jest.fn()}
                    updateResult={{ status: "NOT_STARTED" }}
                    resetUpdateStatus={jest.fn()}
                />
            )
            const nickNameInput = screen.getByPlaceholderText("Common:nickname") as HTMLInputElement
            const langSelect = screen.getByTestId(UPDATE_PROFILE_LANGUAGE_SELECT) as HTMLSelectElement
            expect(nickNameInput.value).toEqual(exampleUserNickname)
            expect(langSelect.value).toBe("Pl")
        })
    })

    it("should reset nickname to default value", () => {
        render(
            <UpdateProfileForm
                currentData={{
                    id: exampleUserId,
                    nickName: exampleUserNickname,
                    language: "Pl",
                    emailHash: exampleHashedEmail,
                }}
                t={tFunctionMock}
                updateProfile={jest.fn()}
                updateResult={{ status: "NOT_STARTED" }}
                resetUpdateStatus={jest.fn()}
            />
        )
        const nickNameInput = screen.getByPlaceholderText("Common:nickname") as HTMLInputElement
        expect(nickNameInput.value).toEqual(exampleUserNickname)
        fireEvent.input(nickNameInput, { target: { value: "new-nick-name" } })
        expect(nickNameInput.value).toBe("new-nick-name")
        const resetButton = screen.getByText("Common:form-reset")
        fireEvent.click(resetButton)
        expect(nickNameInput.value).toEqual(exampleUserNickname)
    })

    it("should reset language to default value", () => {
        render(
            <UpdateProfileForm
                currentData={{
                    id: exampleUserId,
                    nickName: exampleUserNickname,
                    language: "Pl",
                    emailHash: exampleHashedEmail,
                }}
                t={tFunctionMock}
                updateProfile={jest.fn()}
                updateResult={{ status: "NOT_STARTED" }}
                resetUpdateStatus={jest.fn()}
            />
        )
        const langSelect = screen.getByTestId(UPDATE_PROFILE_LANGUAGE_SELECT) as HTMLSelectElement
        expect(langSelect.value).toBe("Pl")
        fireEvent.click(langSelect)
        fireEvent.change(langSelect, { target: { value: "En" } })
        expect(langSelect.value).toBe("En")
        const resetButton = screen.getByText("Common:form-reset")
        fireEvent.click(resetButton)
        expect(langSelect.value).toBe("Pl")
    })

    it("should update default value for nickname after success action", () => {
        render(
            <UpdateProfileForm
                currentData={{
                    id: exampleUserId,
                    nickName: exampleUserNickname,
                    language: "Pl",
                    emailHash: exampleHashedEmail,
                }}
                t={tFunctionMock}
                updateProfile={jest.fn()}
                updateResult={{
                    status: "FINISHED",
                    params: { nickName: "updated-nick-name" },
                    data: {
                        id: exampleUserId,
                        nickName: "updated-nick-name",
                        language: "Pl",
                        emailHash: exampleHashedEmail,
                    },
                }}
                resetUpdateStatus={jest.fn()}
            />
        )
        const nickNameInput = screen.getByPlaceholderText("Common:nickname") as HTMLInputElement
        fireEvent.input(nickNameInput, { target: { value: "new-nick-name" } })
        expect(nickNameInput.value).toBe("new-nick-name")
        const resetButton = screen.getByText("Common:form-reset")
        fireEvent.click(resetButton)
        expect(nickNameInput.value).toBe("updated-nick-name")
    })

    it("should update default value for language after success action", () => {
        render(
            <UpdateProfileForm
                currentData={{
                    id: exampleUserId,
                    nickName: exampleUserNickname,
                    language: "Pl",
                    emailHash: exampleHashedEmail,
                }}
                t={tFunctionMock}
                updateProfile={jest.fn()}
                updateResult={{
                    status: "FINISHED",
                    params: { language: "En" },
                    data: {
                        id: exampleUserId,
                        nickName: "updated-nick-name",
                        language: "En",
                        emailHash: exampleHashedEmail,
                    },
                }}
                resetUpdateStatus={jest.fn()}
            />
        )
        const langSelect = screen.getByTestId(UPDATE_PROFILE_LANGUAGE_SELECT) as HTMLSelectElement
        fireEvent.change(langSelect, { target: { value: "Pl" } })
        expect(langSelect.value).toBe("Pl")
        const resetButton = screen.getByText("Common:form-reset")
        fireEvent.click(resetButton)
        expect(langSelect.value).toBe("En")
    })
})
