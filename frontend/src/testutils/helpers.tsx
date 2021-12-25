import { render, RenderOptions } from "@testing-library/react"
import React from "react"
import { Provider } from "react-redux"
import { MemoryRouter, Route, Routes } from "react-router"
import { BrowserRouter } from "react-router-dom"
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

export const renderWithRoute = (path: string) => {
    const Wrapper: React.ComponentType = ({ children }) => (
        <BrowserRouter>
            <Routes>
                <Route path={path} element={children} />
                <Route path="*" element={<div>{`Dummy view for ${window.location.pathname}`}</div>} />
            </Routes>
        </BrowserRouter>
    )

    return (ui: React.ReactElement, options?: Omit<RenderOptions, "queries">) =>
        render(ui, { wrapper: Wrapper, ...options })
}
