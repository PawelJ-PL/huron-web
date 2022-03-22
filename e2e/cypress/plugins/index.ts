import { dbSeed } from "./tasks/db_seed"
// ***********************************************************
// This example plugins/index.js can be used to load plugins
//
// You can change the location of this file or turn off loading
// the plugins file with the 'pluginsFile' configuration option.
//
// You can read more here:
// https://on.cypress.io/plugins-guide
// ***********************************************************

// This function is called when a project is opened or re-opened (e.g. due to
// the project's config changing)

/**
 * @type {Cypress.PluginConfig}
 */
// eslint-disable-next-line no-unused-vars
module.exports = (on, config) => {
    // `on` is used to hook into various events Cypress emits
    // `config` is the resolved Cypress config
    on("task", {
        async "db:seed"() {
            await dbSeed(
                config.env.DB_HOST,
                config.env.DB_PORT,
                config.env.DB_USER,
                config.env.DB_PASSWORD,
                config.env.DB_NAME
            )
            return null
        },
    })
}
