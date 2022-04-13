import React from "react"
import { Route, Routes } from "react-router"
import { AppRoute } from "./AppRoute"
import DefaultLayout from "../layouts/default/DefaultLayout"

type Props = {
    routes: AppRoute[]
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    defaultComponent: React.ComponentType<any>
}

const AppRouter: React.FC<Props> = ({ routes, defaultComponent }) => {
    const DefaultComponent = defaultComponent

    return (
        <Routes>
            {generateRoutes(routes).concat(<Route path="*" key={routes.length} element={<DefaultComponent />} />)}
        </Routes>
    )
}

const generateRoutes = (routes: AppRoute[]) =>
    routes.map((route) => <Route key={route.path} path={route.path} element={renderElement(route)} />)

const renderElement = (route: AppRoute) => {
    if (route.withLayout) {
        const Elem = route.element
        return <DefaultLayout>{Elem}</DefaultLayout>
    } else {
        return route.element
    }
}

export default AppRouter
