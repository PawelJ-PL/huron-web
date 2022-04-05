import { login } from "./login"

describe("User login", () => {
    beforeEach(() => {
        cy.visit("/")
    })

    it("should redirect to collection after login", () => {
        cy.intercept({ url: "/api/v1/users/auth/login", method: "POST" }).as("loginRequest")
        login(Cypress.env("FIRST_USER_EMAIL"), Cypress.env("FIRST_USER_PASSWORD"))
        cy.wait("@loginRequest", { timeout: 15000 })
        cy.url().should("include", "/collection/0d1f9854-e043-4e96-80cc-cf193ba152d2")
    })

    it("should show message on failed login", () => {
        cy.intercept({ url: "/api/v1/users/auth/login", method: "POST" }).as("loginRequest")
        login(Cypress.env("FIRST_USER_EMAIL"), "invalid-password")
        cy.wait("@loginRequest", { timeout: 15000 })
        cy.contains("Invalid username or password")
        cy.url().should("not.include", "/collection")
    })
})
