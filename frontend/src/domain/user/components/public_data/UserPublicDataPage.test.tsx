import React from "react"
import { UserPublicDataPage } from "./UserPublicDataPage"
import { render } from "@testing-library/react"
import { tFunctionMock } from "../../../../testutils/mocks/i18n-mock"
import { exampleUserPublicData } from "../../../../testutils/constants/user"

// eslint-disable-next-line react/display-name
jest.mock("./PublicUserDataActionsPanel", () => () => <div data-testid="PUBLIC_USER_DATA_ACTIONS_PANEL_MOCK"></div>)

describe("User public data page", () => {
    describe("mount and unmount", () => {
        it("should reset status on mount", () => {
            const resetRemoveResult = jest.fn()
            const resetCreateResult = jest.fn()
            const resetEditResult = jest.fn()

            render(
                <UserPublicDataPage
                    actionInProgress={false}
                    removeContactResult={{ status: "NOT_STARTED" }}
                    createContactResult={{ status: "NOT_STARTED" }}
                    editContactResult={{ status: "NOT_STARTED" }}
                    resetCreateContactResult={resetCreateResult}
                    resetRemoveContactResult={resetRemoveResult}
                    resetEditContactResult={resetEditResult}
                    t={tFunctionMock}
                    userPublicData={exampleUserPublicData}
                    self={false}
                />
            )

            expect(resetRemoveResult).toHaveBeenCalledTimes(1)
            expect(resetCreateResult).toHaveBeenCalledTimes(1)
            expect(resetEditResult).toHaveBeenCalledTimes(1)
        })

        it("should reset status on unmount", () => {
            const resetRemoveResult = jest.fn()
            const resetCreateResult = jest.fn()
            const resetEditResult = jest.fn()

            const view = render(
                <UserPublicDataPage
                    actionInProgress={false}
                    removeContactResult={{ status: "NOT_STARTED" }}
                    createContactResult={{ status: "NOT_STARTED" }}
                    editContactResult={{ status: "NOT_STARTED" }}
                    resetCreateContactResult={resetCreateResult}
                    resetRemoveContactResult={resetRemoveResult}
                    resetEditContactResult={resetEditResult}
                    t={tFunctionMock}
                    userPublicData={exampleUserPublicData}
                    self={false}
                />
            )

            view.unmount()

            expect(resetRemoveResult).toHaveBeenCalledTimes(2)
            expect(resetCreateResult).toHaveBeenCalledTimes(2)
            expect(resetEditResult).toHaveBeenCalledTimes(2)
        })
    })
})
