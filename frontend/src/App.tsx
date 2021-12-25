import React from "react"
import AuthDecisionContainer from "./application/AuthDecisionContainer"
import { BrowserRouter } from "react-router-dom"
import { ChakraProvider } from "@chakra-ui/react"
import { theme } from "./application/theme"
import { Provider } from "react-redux"
import applicationStore from "./application/store"
import TranslatedApp from "./application/localization/components/TranslatedApp"

function App(): JSX.Element {
    return (
        <Provider store={applicationStore}>
            <ChakraProvider theme={theme}>
                <BrowserRouter>
                    <TranslatedApp>
                        <AuthDecisionContainer />
                    </TranslatedApp>
                </BrowserRouter>
            </ChakraProvider>
        </Provider>
    )
}

export default App
