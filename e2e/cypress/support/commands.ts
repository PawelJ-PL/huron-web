// ***********************************************
// This example commands.js shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************
//
//
// -- This is a parent command --
// Cypress.Commands.add('login', (email, password) => { ... })
//
//
// -- This is a child command --
// Cypress.Commands.add('drag', { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add('dismiss', { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This will overwrite an existing command --
// Cypress.Commands.overwrite('visit', (originalFn, url, options) => { ... })

export type GetOptions = Partial<Cypress.Loggable & Cypress.Timeoutable & Cypress.Withinable & Cypress.Shadow>

Cypress.Commands.add("loginByApi", (email: string, password: string) => {
    cy.request({ url: "/api/v1/users/auth/login", method: "POST", body: { email, password } })
})

Cypress.Commands.add("getByTestId", (testId: string, options?: GetOptions) =>
    cy.get(`[data-testid="${testId}"]`, options)
)

Cypress.Commands.add("getByRole", (role: string, options?: GetOptions) => cy.get(`[role="${role}"]`, options))

Cypress.Commands.add("getByAriaLabel", (label: string, options?: GetOptions) =>
    cy.get(`[aria-label="${label}"]`, options)
)

Cypress.Commands.add("containsAlert", (text: string | number | RegExp) => cy.contains("[role=alert]", text))

Cypress.Commands.add("unlockMasterKey", (password: string) => {
    cy.getByTestId("LOCK_KEY_BUTTON").click()
    cy.get("input[type=password]").type(password)
    cy.contains("button", "Unlock").click()
    cy.getByAriaLabel("The encryption key is unlocked")
})
