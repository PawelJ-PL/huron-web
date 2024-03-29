import { createRoot } from "react-dom/client"
import React from "react"
import App from "./App"
import reportWebVitals from "./reportWebVitals"
import "react-datepicker/dist/react-datepicker.css"
import "./application/localization/i18n"

const container = document.getElementById("root") as HTMLElement
const root = createRoot(container)
root.render(<App />)

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals()
