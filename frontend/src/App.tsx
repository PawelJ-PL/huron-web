import React from "react"
import AuthDecisionContainer from "./application/AuthDecisionContainer"
import { Router } from "react-router-dom"
import { history } from "./application/history"
import { ChakraProvider } from "@chakra-ui/react"
import { theme } from "./application/theme"
import { Provider } from "react-redux"
import applicationStore from "./application/store"
import TranslatedApp from "./application/localization/components/TranslatedApp"

function App(): JSX.Element {
    return (
        <Provider store={applicationStore}>
            <ChakraProvider theme={theme}>
                <Router history={history}>
                    <TranslatedApp>
                        <AuthDecisionContainer />
                    </TranslatedApp>
                </Router>
            </ChakraProvider>
        </Provider>
    )
}

export default App
