import { maybeIt } from "../../../utils/conditionalRuns"
import { clearCookieAndFetchUserData, createApiKeyAndReload } from "./user_settings"

describe("User settings", () => {
    beforeEach(() => {
        cy.viewport(1920, 1024)
        cy.loginByApi(Cypress.env("FIRST_USER_EMAIL"), Cypress.env("FIRST_USER_API_PASSWORD"))
        cy.visit("/profile")
    })

    describe("user profile tab", () => {
        beforeEach(() => {
            cy.get("div").contains("User profile").click({ force: true })
        })

        it("should show user profile", () => {
            cy.get("input#nickname").invoke("val").should("equal", "firstuser")
            cy.get("select#language").contains("English")
        })

        it("should update nickname", () => {
            const newNickName = "newNickName"
            cy.get("input#nickname").clear().type(newNickName)
            cy.get("button").contains("Save").click()
            cy.getByTestId("USER_PROFILE_BUTTON").contains(newNickName)
        })

        it("should change language", () => {
            cy.get("select#language").select("Pl")
            cy.get("button").contains("Save").click()
            cy.contains("Profil użytkownika")
            cy.getByTestId("LANGUAGE_SELECTION").contains("Polski")
        })

        it("should change language from navbar", () => {
            cy.getByTestId("LANGUAGE_SELECTION").click({ force: true })
            cy.getByTestId("LANGUAGE_MENU_ITEM_POLSKI").click()
            cy.contains("Profil użytkownika")
        })
    })

    describe("change password tab", () => {
        beforeEach(() => {
            cy.get("div").contains("Change password").click()
        })

        // Skipped because test fails in docker because of node forge primes.worker
        maybeIt("should change password", () => {
            const newPassword = "updated-password"
            const newApiPassword = "371f334ff5778598e19da9fb3ed46952e6fe44219eea78a41d886ec710db5eac"
            cy.get("input#email").type(Cypress.env("FIRST_USER_EMAIL"))
            cy.get("input#oldPassword").type(Cypress.env("FIRST_USER_PASSWORD"))
            cy.get("input#newPassword").type(newPassword)
            cy.get("input#confirmNewPassword").type(newPassword)
            cy.get("button").contains("Change password").click()
            cy.getByRole("alert", { timeout: 20000 }).contains("The password has been changed.")
            cy.loginByApi(Cypress.env("FIRST_USER_EMAIL"), newApiPassword).its("status").should("equal", 200)
        })
    })

    describe("api keys tab", () => {
        beforeEach(() => {
            cy.intercept({ url: "/api/v1/users/me/api-keys", method: "GET" }).as("listApiKeys")
            cy.get("div").contains("API keys").click()
            cy.wait("@listApiKeys")
        })

        it("should create valid API key", () => {
            cy.contains("button", "Create new API key").click()
            cy.get("input#description").type("Test Key")
            cy.intercept({ url: `/api/v1/users/me/api-keys`, method: "POST" }).as("createApiKey")
            cy.get("button")
                .contains(/^Create$/)
                .click()
            cy.wait("@createApiKey")
            cy.getByAriaLabel("Show API key").click()
            cy.getByTestId("API_KEY_VALUE").then((element) =>
                clearCookieAndFetchUserData(element.text()).its("body").should("contain", { nickName: "firstuser" })
            )
        })

        it("should disable API key", () => {
            createApiKeyAndReload()
            cy.wait("@listApiKeys")
            cy.getByAriaLabel("Show API key").click()
            cy.intercept({ url: "/api/v1/users/me/api-keys/*", method: "PATCH" }).as("editApiKey")
            cy.getByTestId("DISABLE_API_KEY_CHECKBOX").click()
            cy.wait("@editApiKey")
            cy.getByTestId("API_KEY_VALUE").then((element) =>
                clearCookieAndFetchUserData(element.text(), false).its("status").should("equal", 401)
            )
        })

        it("should delete API key", () => {
            createApiKeyAndReload()
            cy.wait("@listApiKeys")
            cy.getByAriaLabel("Show API key").click()
            cy.getByTestId("API_KEY_VALUE").then((element) => {
                cy.getByAriaLabel("delete").click()
                cy.get("button").contains("Confirm").click()
                cy.get("div").contains("No API key has been created")
                clearCookieAndFetchUserData(element.text(), false).its("status").should("equal", 401)
            })
        })

        it("should expire API key", () => {
            createApiKeyAndReload()
            cy.wait("@listApiKeys")
            cy.getByAriaLabel("Show API key").click()
            cy.intercept({ url: "/api/v1/users/me/api-keys/*", method: "PATCH" }).as("editApiKey")
            cy.getByTestId("API_KEY_VALUE").then((element) => {
                cy.getByTestId("API_KEY_EXPIRATION_TIME").click()
                cy.getByTestId("API_KEY_EXPIRATION_SWITCH").click()
                cy.getByTestId("API_KEY_EXPIRATION_PICKER_INPUT").clear().type("01/01/1980, 11:00 PM").type("{esc}")
                cy.get("button").contains("Confirm").click()
                cy.wait("@editApiKey")
                clearCookieAndFetchUserData(element.text(), false).its("status").should("equal", 401)
            })
        })
    })
})
