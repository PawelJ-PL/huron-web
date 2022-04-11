import { createDirectoryUsingApi } from "./directory"

describe("Directory operations", () => {
    beforeEach(() => {
        cy.viewport(1920, 1024)
        cy.loginByApi(Cypress.env("FIRST_USER_EMAIL"), Cypress.env("FIRST_USER_API_PASSWORD"))
        cy.visit("/")
    })

    it("should create a new directory", () => {
        const newDirName = "First directory"

        cy.contains("h3", "Empty directory")
        cy.contains("button", "New directory").click()
        cy.getByTestId("NEW_DIRECTORY_NAME_INPUT").type(newDirName)
        cy.contains("button", "Create").click()
        cy.contains("a", newDirName).click()
        cy.get("nav[aria-label=breadcrumb] ol li").invoke("text").should("equal", "Test collection/First directory")
    })

    it("should rename directory", () => {
        const directoryName = "Example directory"
        const newName = "Updated directory name"
        createDirectoryUsingApi(Cypress.env("TEST_COLLECTION_ID"), directoryName)
        cy.reload()

        cy.contains("button", "Actions").click()
        cy.contains("[role=menuitem]", "Rename").click()
        cy.get("input#newName").clear().type(newName)
        cy.contains("button", "Confirm").click()
        cy.contains("a", newName)
    })

    it("should delete directory", () => {
        const directoryName = "Example directory"
        createDirectoryUsingApi(Cypress.env("TEST_COLLECTION_ID"), directoryName)
        cy.reload()

        cy.contains("button", "Actions").click()
        cy.contains("[role=menuitem]", "Delete").click()
        cy.contains("button", "Confirm").click()
        cy.contains("h3", "Empty directory")
    })

    it("should not delete non empty directory", () => {
        const directoryName = "Example directory"
        createDirectoryUsingApi(Cypress.env("TEST_COLLECTION_ID"), directoryName).then((parentId) => {
            createDirectoryUsingApi(Cypress.env("TEST_COLLECTION_ID"), "Child Directory", parentId.body.id)
        })
        cy.reload()

        cy.contains("button", "Actions").click()
        cy.contains("[role=menuitem]", "Delete").click()
        cy.contains("button", "Confirm").click()
        cy.containsAlert("The directory could not be deleted because it was not empty")
        cy.contains("a", directoryName).click()
    })

    it("should delete non empty directory if allowed", () => {
        const directoryName = "Example directory"
        const newName = "Updated directory name"
        createDirectoryUsingApi(Cypress.env("TEST_COLLECTION_ID"), directoryName).then((parentId) => {
            createDirectoryUsingApi(Cypress.env("TEST_COLLECTION_ID"), "Child Directory", parentId.body.id)
        })
        cy.reload()

        cy.contains("button", "Actions").click()
        cy.contains("[role=menuitem]", "Delete").click()
        cy.contains("Delete non empty directories").click()
        cy.contains("button", "Confirm").click()
        cy.contains("h3", "Empty directory")
    })
})
