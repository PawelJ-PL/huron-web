import ky, { AfterResponseHook } from "ky"
import PackageJson from "../../../package.json"
import { Unauthorized } from "./ApiError"

const ENDPOINTS_WITH_POSSIBLE_401 = ["/api/v1/users/auth/login"]

const logoutOnUnauthorizedHood: AfterResponseHook = (request, options, response) => {
    if (response.status === 401) {
        const requestUrl = new URL(request.url)
        if (ENDPOINTS_WITH_POSSIBLE_401.includes(requestUrl.pathname)) {
            return
        }
        return Promise.reject(new Unauthorized(true))
    }
}

let currentCsrfToken: string | undefined = undefined

const createBaseClient = () =>
    ky.create({
        prefixUrl: process.env.REACT_APP_API_PREFIX + "api/v1",
        retry: 0,
        headers: {
            Accept: "application/json",
            "X-App-Version": `huron-web/${PackageJson.version}`,
            "X-Csrf-Token": currentCsrfToken,
        },
        hooks: { afterResponse: [logoutOnUnauthorizedHood] },
    })

export let client: typeof ky = createBaseClient()

export const onStateChange: (t: string | null) => void = (csrfToken: string | null) => {
    const token = csrfToken ?? undefined
    if (token !== currentCsrfToken) {
        currentCsrfToken = token
        client = createBaseClient()
    }
}
