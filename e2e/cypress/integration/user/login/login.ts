export const login = (email: string, password: string) => {
    cy.get("input#email").type(email)
    cy.get("input#password").type(password)
    cy.get("button:submit").click()
}
