export const maybeIt = Cypress.env("IN_DOCKER") ? it.skip : it
