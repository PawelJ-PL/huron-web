import { fireEvent, render, screen } from "@testing-library/react"
import React from "react"
import { Provider } from "react-redux"
import { Action, combineReducers, compose, createStore, applyMiddleware } from "redux"
import { createEpicMiddleware } from "redux-observable"
import { AppState } from "../../../../application/store"
import { usersEpics } from "../../store/Epics"
import { usersReducer } from "../../store/Reducers"
import DisplayOptionsPanel from "./DisplayOptionsPanel"
import { FIND_USERS_INPUT } from "./testids"
import UsersApi from "../../api/UsersApi"

jest.mock("react-i18next", () => ({
    withTranslation: () => (Component: React.ComponentClass | React.FC) => {
        Component.defaultProps = { ...Component.defaultProps, t: (k: string) => k }
        return Component
    },
}))

describe("Display options panel", () => {
    describe("Add contact flow", () => {
        it("should open two modals one after another", async () => {
            jest.spyOn(UsersApi, "findUserByNickName").mockResolvedValue({
                result: [
                    { userId: "1", nickName: "foobar1" },
                    { userId: "2", nickName: "foobar2" },
                    { userId: "3", nickName: "foobar3" },
                ],
                page: 1,
                elementsPerPage: 3,
                totalPages: 1,
            })
            const reducer = combineReducers({ users: usersReducer })
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const composeEnhancer: typeof compose = (window as any).__REDUX_DEVTOOLS_EXTENSION_COMPOSE__ || compose
            const epicMiddleware = createEpicMiddleware<Action, Action, AppState>()
            const store = createStore(reducer, composeEnhancer(applyMiddleware(epicMiddleware)))
            epicMiddleware.run(usersEpics)
            render(
                <Provider store={store}>
                    <DisplayOptionsPanel defaultEntries={10} defaultFilterValue={""} />
                </Provider>
            )
            const addContactButton = screen.getByText("contacts-list:add-contact-button-label")
            fireEvent.click(addContactButton)
            const findUserInput = await screen.findByTestId(FIND_USERS_INPUT)
            fireEvent.change(findUserInput, { target: { value: "foobar" } })
            const entry2 = await screen.findByText("foobar2", undefined, { timeout: 2000 })
            fireEvent.click(entry2)
            const modalHeader = await screen.findByText("user-public-page:add-or-edit-contact-modal.add-contact-header")
            expect(modalHeader).toBeInTheDocument()
        })
    })
})
