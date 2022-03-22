describe("User settings", () => {
    beforeEach(() => {
        cy.loginByApi(Cypress.env("FIRST_USER_EMAIL"), Cypress.env("FIRST_USER_API_PASSWORD"))
        cy.visit("/profile")
    })

    it("show user profile", () => {
        cy.get("input#nickname").invoke("val").should("equal", "firstuser")
        cy.get("select#language").contains("English")
    })
})
