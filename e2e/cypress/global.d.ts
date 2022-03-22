declare namespace Cypress {
    interface Chainable {
        loginByApi(email: string, password: string): Chainable<Response>
    }
}
