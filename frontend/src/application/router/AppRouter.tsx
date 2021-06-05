import React from "react"
import { Route, Switch } from "react-router"
import { AppRoute } from "./AppRoute"
import partition from "lodash/partition"
import DefaultLayout from "../layouts/default/DefaultLayout"

type Props = {
    routes: AppRoute[]
    defaultComponent: React.ComponentType<unknown>
}

const AppRouter: React.FC<Props> = ({ routes, defaultComponent }) => {
    const [withLayout, withoutLayout] = partition(routes, (r) => r.withLayout)

    return (
        <Switch>
            <Route exact={true} path={withLayout.map((r) => r.path)}>
                <DefaultLayout>
                    <Switch>{generateRoutes(withLayout)}</Switch>
                </DefaultLayout>
            </Route>
            {generateRoutes(withoutLayout).concat(<Route key={routes.length} component={defaultComponent} />)}
        </Switch>
    )
}

const generateRoutes = (routes: AppRoute[]) =>
    routes.map((route) => (
        <Route
            key={route.path}
            exact={route.exact}
            path={route.path}
            component={route.component}
            render={route.render}
        />
    ))

export default AppRouter
