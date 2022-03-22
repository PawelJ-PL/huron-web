Cypress.on("window:before:load", (window) => {
    Object.defineProperty(window.navigator, "language", { value: "en-GB" })
    Object.defineProperty(window.navigator, "languages", ["en-GB"])
})
