export const clearCookieAndFetchUserData = (apiKey: string, failOnUnexpectedCode?: boolean) => {
    cy.clearCookies()
    return cy.request({
        url: "/api/v1/users/me/data",
        headers: { "X-Api-Key": apiKey },
        failOnStatusCode: failOnUnexpectedCode ?? true,
    })
}

export const createApiKeyUsingApi = (description: string) =>
    cy
        .request({ url: "/api/v1/users/me/data" })
        .its("headers")
        .then((headers) =>
            cy.request({
                url: "/api/v1/users/me/api-keys",
                method: "POST",
                body: { description },
                headers: { "X-Csrf-Token": headers["x-csrf-token"] },
            })
        )

export const createApiKeyAndReload = () => {
    createApiKeyUsingApi("Test key")
    cy.reload()
    cy.get("div").contains("API keys").click()
}
