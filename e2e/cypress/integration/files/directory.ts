export const createDirectoryUsingApi = (collectionId: string, name: string, parent?: string) =>
    cy
        .request({ url: "/api/v1/users/me/data" })
        .its("headers")
        .then((headers) =>
            cy.request({
                url: `/api/v1/collections/${collectionId}/files`,
                method: "POST",
                body: { parent, name, "@type": "NewDirectory" },
                headers: { "X-Csrf-Token": headers["x-csrf-token"] },
            })
        )
