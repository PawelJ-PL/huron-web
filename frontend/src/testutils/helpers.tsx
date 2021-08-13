import { render, RenderOptions } from "@testing-library/react"
import React from "react"
import { Provider } from "react-redux"
import { MemoryRouter } from "react-router"
import configureMockStore from "redux-mock-store"

// eslint-disable-next-line @typescript-eslint/explicit-module-boundary-types
export const renderWithStoreAndRouter = (reduxState?: unknown) => {
    const mockStore = configureMockStore()
    const store = mockStore(reduxState)

    const Wrapper: React.ComponentType = ({ children }) => (
        <Provider store={store}>
            <MemoryRouter>{children}</MemoryRouter>
        </Provider>
    )

    return (ui: React.ReactElement, options?: Omit<RenderOptions, "queries">) =>
        render(ui, { wrapper: Wrapper, ...options })
}
