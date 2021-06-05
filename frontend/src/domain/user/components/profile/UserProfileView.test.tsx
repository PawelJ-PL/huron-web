import { fireEvent, render, screen } from "@testing-library/react"
import React from "react"
import { exampleUserId, exampleUserNickname } from "../../../../testutils/constants/user"
import { tFunctionMock } from "../../../../testutils/mocks/i18n-mock"
import { UserProfileView } from "./UserProfileView"

// eslint-disable-next-line react/display-name
jest.mock("./UpdateProfileForm", () => () => <div data-testid="user-profile-form"></div>)

// eslint-disable-next-line react/display-name
jest.mock("./ChangePasswordForm", () => () => <div data-testid="change-password-form"></div>)

// eslint-disable-next-line react/display-name
jest.mock("./ApiKeysView", () => () => <div data-testid="api-keys"></div>)

jest.mock("@chakra-ui/media-query", () => ({
    ...jest.requireActual("@chakra-ui/media-query"),
    useBreakpointValue: () => false,
}))

describe("User profile view", () => {
    it("should show user profile form on mount", () => {
        render(
            <UserProfileView
                userData={{ id: exampleUserId, nickName: exampleUserNickname, language: "Pl" }}
                t={tFunctionMock}
            />
        )
        screen.getByTestId("user-profile-form")
    })

    it("should switch view to password change", () => {
        render(
            <UserProfileView
                userData={{ id: exampleUserId, nickName: exampleUserNickname, language: "Pl" }}
                t={tFunctionMock}
            />
        )
        const changePasswordLink = screen.getByText("profile-page:sections.change-password")
        fireEvent.click(changePasswordLink)
        screen.getByTestId("change-password-form")
    })

    it("should switch view to api keys", () => {
        render(
            <UserProfileView
                userData={{ id: exampleUserId, nickName: exampleUserNickname, language: "Pl" }}
                t={tFunctionMock}
            />
        )
        const apiKeysLink = screen.getByText("profile-page:sections.api-keys")
        fireEvent.click(apiKeysLink)
        screen.getByTestId("api-keys")
    })

    it("should switch view to user profile form", () => {
        render(
            <UserProfileView
                userData={{ id: exampleUserId, nickName: exampleUserNickname, language: "Pl" }}
                t={tFunctionMock}
            />
        )
        const apiKeysLink = screen.getByText("profile-page:sections.api-keys")
        fireEvent.click(apiKeysLink)
        screen.getByTestId("api-keys")
        const userProfileLink = screen.getByText("profile-page:sections.user-profile")
        fireEvent.click(userProfileLink)
        screen.getByTestId("user-profile-form")
    })
})
