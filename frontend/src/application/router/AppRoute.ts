/* eslint-disable @typescript-eslint/no-explicit-any */

import { RouteComponentProps } from "react-router-dom"

export type AppRoute = {
    path: string
    exact: boolean
    component?: React.ComponentType<RouteComponentProps<any>> | React.ComponentType<any>
    render?: (props: RouteComponentProps<any>) => React.ReactNode
    withLayout?: boolean
}
