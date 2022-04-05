declare namespace Cypress {
    interface Chainable {
        loginByApi(email: string, password: string): Chainable<Response>
        getByTestId(
            testId: string,
            options?: Partial<Cypress.Loggable & Cypress.Timeoutable & Cypress.Withinable & Cypress.Shadow>
        ): Chainable<JQuery<HTMLElement>>
        getByRole(
            role: string,
            options?: Partial<Cypress.Loggable & Cypress.Timeoutable & Cypress.Withinable & Cypress.Shadow>
        ): Chainable<JQuery<HTMLElement>>
        getByAriaLabel(
            label: string,
            options?: Partial<Cypress.Loggable & Cypress.Timeoutable & Cypress.Withinable & Cypress.Shadow>
        ): Chainable<JQuery<HTMLElement>>
    }
}
