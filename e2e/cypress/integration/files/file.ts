export const uploadFileUsingApi = (params: {
    collectionId: string
    name: string
    contentDigest: string
    iv: string
    encryptionKeyVersion: string
    bytes: string
    parent?: string
    mimeType?: string
}) =>
    cy
        .request({ url: "/api/v1/users/me/data" })
        .its("headers")
        .then((headers) =>
            cy.request({
                url: `/api/v1/collections/${params.collectionId}/files`,
                method: "POST",
                body: {
                    params: params.parent,
                    name: params.name,
                    "@type": "NewFile",
                    mimeType: params.mimeType,
                    contentDigest: params.contentDigest,
                    content: {
                        algorithm: "AES-CBC",
                        iv: params.iv,
                        encryptionKeyVersion: params.encryptionKeyVersion,
                        bytes: params.bytes,
                    },
                },
                headers: { "X-Csrf-Token": headers["x-csrf-token"] },
            })
        )
