import { screen } from "@testing-library/react"
import React from "react"
import { exampleHashedEmail, exampleUserId, exampleUserNickname } from "../../../../../testutils/constants/user"
import { renderWithRoute } from "../../../../../testutils/helpers"
import { tFunctionMock } from "../../../../../testutils/mocks/i18n-mock"
import { Author } from "./Author"

const renderWithPath = renderWithRoute("/")

describe("Author", () => {
    it("should render user removed if authorId is undefined", () => {
        renderWithPath(
            <Author
                authorId={undefined}
                t={tFunctionMock}
                userDataResult={{
                    status: "FINISHED",
                    params: undefined,
                    data: {
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "pl",
                        emailHash: exampleHashedEmail,
                    },
                }}
                knownUsers={{
                    foo: { status: "FINISHED", params: "foo", data: { userId: "foo", nickName: "n1" } },
                    bar: { status: "FINISHED", params: "bar", data: { userId: "bar", nickName: "n2" } },
                }}
            />
        )
        const userName = screen.getByText("file-view:file-data.account-deleted")
        expect(userName).toBeInTheDocument()
    })

    it("should render self nickname", () => {
        renderWithPath(
            <Author
                authorId={exampleUserId}
                t={tFunctionMock}
                userDataResult={{
                    status: "FINISHED",
                    params: undefined,
                    data: {
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "pl",
                        emailHash: exampleHashedEmail,
                    },
                }}
                knownUsers={{
                    foo: { status: "FINISHED", params: "foo", data: { userId: "foo", nickName: "n1" } },
                    bar: { status: "FINISHED", params: "bar", data: { userId: "bar", nickName: "n2" } },
                }}
            />
        )
        const userName = screen.getByText(exampleUserNickname)
        expect(userName).toBeInTheDocument()
    })

    it("should render contact alias", () => {
        renderWithPath(
            <Author
                authorId={"bar"}
                t={tFunctionMock}
                userDataResult={{
                    status: "FINISHED",
                    params: undefined,
                    data: {
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "pl",
                        emailHash: exampleHashedEmail,
                    },
                }}
                knownUsers={{
                    foo: {
                        status: "FINISHED",
                        params: "foo",
                        data: { userId: "foo", nickName: "nick1", contactData: { alias: "alias1" } },
                    },
                    bar: {
                        status: "FINISHED",
                        params: "bar",
                        data: { userId: "bar", nickName: "nick2", contactData: { alias: "alias2" } },
                    },
                }}
            />
        )
        const userName = screen.getByText("alias2")
        expect(userName).toBeInTheDocument()
    })

    it("should render another users nickname if no contact data exists", () => {
        renderWithPath(
            <Author
                authorId={"bar"}
                t={tFunctionMock}
                userDataResult={{
                    status: "FINISHED",
                    params: undefined,
                    data: {
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "pl",
                        emailHash: exampleHashedEmail,
                    },
                }}
                knownUsers={{
                    foo: {
                        status: "FINISHED",
                        params: "foo",
                        data: { userId: "foo", nickName: "nick1", contactData: { alias: "alias1" } },
                    },
                    bar: {
                        status: "FINISHED",
                        params: "bar",
                        data: { userId: "bar", nickName: "nick2" },
                    },
                }}
            />
        )
        const userName = screen.getByText("nick2")
        expect(userName).toBeInTheDocument()
    })

    it("should render another users nickname if alias not defined", () => {
        renderWithPath(
            <Author
                authorId={"bar"}
                t={tFunctionMock}
                userDataResult={{
                    status: "FINISHED",
                    params: undefined,
                    data: {
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "pl",
                        emailHash: exampleHashedEmail,
                    },
                }}
                knownUsers={{
                    foo: {
                        status: "FINISHED",
                        params: "foo",
                        data: { userId: "foo", nickName: "nick1", contactData: { alias: "alias1" } },
                    },
                    bar: {
                        status: "FINISHED",
                        params: "bar",
                        data: { userId: "bar", nickName: "nick2", contactData: {} },
                    },
                }}
            />
        )
        const userName = screen.getByText("nick2")
        expect(userName).toBeInTheDocument()
    })

    it("should render unknown user", () => {
        renderWithPath(
            <Author
                authorId={"baz"}
                t={tFunctionMock}
                userDataResult={{
                    status: "FINISHED",
                    params: undefined,
                    data: {
                        id: exampleUserId,
                        nickName: exampleUserNickname,
                        language: "pl",
                        emailHash: exampleHashedEmail,
                    },
                }}
                knownUsers={{
                    foo: { status: "FINISHED", params: "foo", data: { userId: "foo", nickName: "n1" } },
                    bar: { status: "FINISHED", params: "bar", data: { userId: "bar", nickName: "n2" } },
                }}
            />
        )
        const userName = screen.getByText("common:unknown-masculine")
        expect(userName).toBeInTheDocument()
    })
})
