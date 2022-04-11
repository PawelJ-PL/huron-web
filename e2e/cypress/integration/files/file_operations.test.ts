import { uploadFileUsingApi } from "./file"
import { b64Content, defaultFileParams } from "./defaultFile"

const downloadDir = Cypress.config("downloadsFolder")
const testResourcesDir = `${Cypress.config("fixturesFolder")}/test_resources`

describe("File operations", () => {
    beforeEach(() => {
        cy.viewport(1920, 1024)
        cy.loginByApi(Cypress.env("FIRST_USER_EMAIL"), Cypress.env("FIRST_USER_API_PASSWORD"))
        cy.visit("/")
    })

    it("should upload file", () => {
        const fileName = "document.png"

        cy.readFile(`${testResourcesDir}/${fileName}`, null).as("testFile")
        cy.intercept({ url: `/api/v1/collections/${Cypress.env("TEST_COLLECTION_ID")}/files`, method: "POST" }).as(
            "uploadFile"
        )
        cy.unlockMasterKey(Cypress.env("FIRST_USER_PASSWORD"))

        cy.contains("button", "Upload file").click()
        cy.contains("Drag & drop file here or click to browse").selectFile("@testFile", { action: "drag-drop" })
        cy.containsAlert(fileName)
        cy.contains("button", "Encrypt and send").click()
        cy.wait("@uploadFile")

        cy.contains("a", fileName)
        cy.contains("image/png")

        cy.contains("button", "Actions").click()
        cy.contains("[role=menuitem]", "Download file").click()
        cy.readFile(`${downloadDir}/${fileName}`, "base64").should("equal", b64Content)
    })

    it("should rename file", () => {
        const newName = "new_name.png"
        uploadFileUsingApi(defaultFileParams)
        cy.reload()

        cy.contains("button", "Actions").click()
        cy.contains("[role=menuitem]", "Rename").click()
        cy.get("input#newName").clear().type(newName)
        cy.contains("button", "Confirm").click()
        cy.contains("a", newName)
    })

    it("should delete file", () => {
        uploadFileUsingApi(defaultFileParams)
        cy.reload()

        cy.contains("button", "Actions").click()
        cy.contains("[role=menuitem]", "Delete").click()
        cy.contains("button", "Confirm").click()
        cy.contains("h3", "Empty directory")
    })

    it("should upload new version", () => {
        const newFileName = "v1.txt"
        const newFileContent = "File content"
        uploadFileUsingApi(defaultFileParams)
        cy.reload()
        cy.unlockMasterKey(Cypress.env("FIRST_USER_PASSWORD"))

        cy.contains("button", "Actions").click()
        cy.contains("[role=menuitem]", "Upload new version").click()
        cy.contains("Drag & drop file here or click to browse").selectFile(
            { contents: Buffer.from(newFileContent), fileName: newFileName, mimeType: "text/plain" },
            { action: "drag-drop" }
        )
        cy.containsAlert(newFileName)
        cy.contains("button", "Encrypt and send").click()

        cy.contains("text/plain")

        cy.contains("button", "Actions").click()
        cy.contains("[role=menuitem]", "Download file").click()
        cy.readFile(`${downloadDir}/${defaultFileParams.name}`, "ascii").should("equal", newFileContent)
    })
})
