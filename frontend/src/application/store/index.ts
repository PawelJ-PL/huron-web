import { onStateChange } from "./../api/BaseClient"
import { usersEpics } from "./../../domain/user/store/Epics"
import { usersReducer } from "./../../domain/user/store/Reducers"
import { Action, applyMiddleware, combineReducers, compose, createStore } from "redux"
import { combineEpics, createEpicMiddleware } from "redux-observable"
import { collectionsEpics } from "../../domain/collection/store/Epics"
import { collectionsReducer } from "../../domain/collection/store/Reducers"

const rootReducer = combineReducers({ users: usersReducer, collections: collectionsReducer })

export type AppState = ReturnType<typeof rootReducer>

const rootEpic = combineEpics(usersEpics, collectionsEpics)

function configure() {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const composeEnhancer: typeof compose = (window as any).__REDUX_DEVTOOLS_EXTENSION_COMPOSE__ || compose
    const epicMiddleware = createEpicMiddleware<Action, Action, AppState>()

    const store = createStore(rootReducer, composeEnhancer(applyMiddleware(epicMiddleware)))

    epicMiddleware.run(rootEpic)
    return store
}

const applicationStore = configure()

applicationStore.subscribe(() => onStateChange(applicationStore.getState().users.csrfToken))

export default applicationStore
