import React from "react"
import { I18nextProvider } from "react-i18next"
import {
    exampleHashedEmail,
    exampleMasterKey,
    examplePrivateKey,
    examplePublicKey,
    exampleUserPassword,
} from "../../../../testutils/constants/user"
import { renderWithStoreAndRouter } from "../../../../testutils/helpers"
import { KeyPair } from "../../../cryptography/types/KeyPair"
import { AsyncOperationResult } from "../../../store/async/AsyncOperationResult"
import UnlockKeyButton, { UnlockKeyButton as UnlockKeyButtonRaw } from "./UnlockKeyButton"
import testI18n from "../../../../testutils/i18next/testI18n"
import { fireEvent, render, screen } from "@testing-library/react"
import { LOCK_KEY_BUTTON, LOCK_KEY_TOOLTIP } from "../testids"
import { tFunctionMock } from "../../../../testutils/mocks/i18n-mock"
import { EncryptionKey } from "../../../../domain/collection/types/EncryptionKey"
import { exampleCollectionId, exampleEncryptionKeyVersion } from "../../../../testutils/constants/collection"

const ENTER_PASSWORD_MODAL = "ENTER_PASSWORD_MODAL"

// eslint-disable-next-line react/display-name,  @typescript-eslint/no-explicit-any
jest.mock("./EnterKeyDialog", () => (props: any) => (
    <div data-testid={ENTER_PASSWORD_MODAL}>{props.isOpen.toString()}</div>
))

describe("Unlock key button", () => {
    it("should render unlocked status if everything unlocked", async () => {
        const masterKeyResult: AsyncOperationResult<{ password: string; emailHash: string }, string, Error> = {
            status: "FINISHED",
            params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
            data: exampleMasterKey,
        }
        const keyPairResult: AsyncOperationResult<string, KeyPair, Error> = {
            status: "FINISHED",
            params: exampleMasterKey,
            data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
        }
        const collectionKeyResult: AsyncOperationResult<
            { collectionId: string; privateKey: string },
            EncryptionKey,
            Error
        > = {
            status: "FINISHED",
            params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
            data: {
                collectionId: exampleCollectionId,
                version: exampleEncryptionKeyVersion,
                key: "secret-collection-key",
            },
        }

        const state = {
            users: { masterKey: masterKeyResult, keyPair: keyPairResult },
            collections: { encryptionKey: collectionKeyResult, activeCollection: exampleCollectionId },
        }
        // eslint-disable-next-line testing-library/render-result-naming-convention
        const renderWithStore = renderWithStoreAndRouter(state)

        renderWithStore(
            <I18nextProvider i18n={testI18n}>
                <UnlockKeyButton userEmailHash={exampleHashedEmail} />
            </I18nextProvider>
        )

        const button = screen.getByTestId(LOCK_KEY_BUTTON)
        fireEvent.mouseOver(button)

        const tooltip = await screen.findByTestId(LOCK_KEY_TOOLTIP)

        expect(button.getAttribute("aria-label")).toEqual("unlock-key-button.key-unlocked-label")
        expect(tooltip.textContent).toEqual("unlock-key-button.key-unlocked-tooltip")
    })

    it("should render unlocked status if collection is key is not unlocked but active collection not defined", async () => {
        const masterKeyResult: AsyncOperationResult<{ password: string; emailHash: string }, string, Error> = {
            status: "FINISHED",
            params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
            data: exampleMasterKey,
        }
        const keyPairResult: AsyncOperationResult<string, KeyPair, Error> = {
            status: "FINISHED",
            params: exampleMasterKey,
            data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
        }
        const collectionKeyResult: AsyncOperationResult<
            { collectionId: string; privateKey: string },
            EncryptionKey,
            Error
        > = { status: "NOT_STARTED" }

        const state = {
            users: { masterKey: masterKeyResult, keyPair: keyPairResult },
            collections: { encryptionKey: collectionKeyResult, activeCollection: null },
        }
        // eslint-disable-next-line testing-library/render-result-naming-convention
        const renderWithStore = renderWithStoreAndRouter(state)

        renderWithStore(
            <I18nextProvider i18n={testI18n}>
                <UnlockKeyButton userEmailHash={exampleHashedEmail} />
            </I18nextProvider>
        )

        const button = screen.getByTestId(LOCK_KEY_BUTTON)
        fireEvent.mouseOver(button)

        const tooltip = await screen.findByTestId(LOCK_KEY_TOOLTIP)

        expect(button.getAttribute("aria-label")).toEqual("unlock-key-button.key-unlocked-label")
        expect(tooltip.textContent).toEqual("unlock-key-button.key-unlocked-tooltip")
    })

    it("should render failed status if master key computes status is failed", async () => {
        const masterKeyResult: AsyncOperationResult<{ password: string; emailHash: string }, string, Error> = {
            status: "FAILED",
            params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
            error: new Error("SomeError"),
        }
        const keyPairResult: AsyncOperationResult<string, KeyPair, Error> = {
            status: "FINISHED",
            params: exampleMasterKey,
            data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
        }
        const collectionKeyResult: AsyncOperationResult<
            { collectionId: string; privateKey: string },
            EncryptionKey,
            Error
        > = {
            status: "FINISHED",
            params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
            data: {
                collectionId: exampleCollectionId,
                version: exampleEncryptionKeyVersion,
                key: "secret-collection-key",
            },
        }

        const state = {
            users: { masterKey: masterKeyResult, keyPair: keyPairResult },
            collections: { encryptionKey: collectionKeyResult, activeCollection: exampleCollectionId },
        }
        // eslint-disable-next-line testing-library/render-result-naming-convention
        const renderWithStore = renderWithStoreAndRouter(state)

        renderWithStore(
            <I18nextProvider i18n={testI18n}>
                <UnlockKeyButton userEmailHash={exampleHashedEmail} />
            </I18nextProvider>
        )

        const button = screen.getByTestId(LOCK_KEY_BUTTON)
        fireEvent.mouseOver(button)

        const tooltip = await screen.findByTestId(LOCK_KEY_TOOLTIP)

        expect(button.getAttribute("aria-label")).toEqual("unlock-key-button.key-error-label")
        expect(tooltip.textContent).toEqual("unlock-key-button.key-error-tooltip")
    })

    it("should render failed status if key pair status is failed", async () => {
        const masterKeyResult: AsyncOperationResult<{ password: string; emailHash: string }, string, Error> = {
            status: "FINISHED",
            params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
            data: exampleMasterKey,
        }
        const keyPairResult: AsyncOperationResult<string, KeyPair, Error> = {
            status: "FAILED",
            params: exampleMasterKey,
            error: new Error("Some error"),
        }
        const collectionKeyResult: AsyncOperationResult<
            { collectionId: string; privateKey: string },
            EncryptionKey,
            Error
        > = {
            status: "FINISHED",
            params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
            data: {
                collectionId: exampleCollectionId,
                version: exampleEncryptionKeyVersion,
                key: "secret-collection-key",
            },
        }

        const state = {
            users: { masterKey: masterKeyResult, keyPair: keyPairResult },
            collections: { encryptionKey: collectionKeyResult, activeCollection: exampleCollectionId },
        }
        // eslint-disable-next-line testing-library/render-result-naming-convention
        const renderWithStore = renderWithStoreAndRouter(state)

        renderWithStore(
            <I18nextProvider i18n={testI18n}>
                <UnlockKeyButton userEmailHash={exampleHashedEmail} />
            </I18nextProvider>
        )

        const button = screen.getByTestId(LOCK_KEY_BUTTON)
        fireEvent.mouseOver(button)

        const tooltip = await screen.findByTestId(LOCK_KEY_TOOLTIP)

        expect(button.getAttribute("aria-label")).toEqual("unlock-key-button.key-error-label")
        expect(tooltip.textContent).toEqual("unlock-key-button.key-error-tooltip")
    })

    it("should render failed status if collection key decryption failed", async () => {
        const masterKeyResult: AsyncOperationResult<{ password: string; emailHash: string }, string, Error> = {
            status: "FINISHED",
            params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
            data: exampleMasterKey,
        }
        const keyPairResult: AsyncOperationResult<string, KeyPair, Error> = {
            status: "FINISHED",
            params: exampleMasterKey,
            data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
        }
        const collectionKeyResult: AsyncOperationResult<
            { collectionId: string; privateKey: string },
            EncryptionKey,
            Error
        > = {
            status: "FAILED",
            params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
            error: new Error("Some error"),
        }

        const state = {
            users: { masterKey: masterKeyResult, keyPair: keyPairResult },
            collections: { encryptionKey: collectionKeyResult, activeCollection: exampleCollectionId },
        }
        // eslint-disable-next-line testing-library/render-result-naming-convention
        const renderWithStore = renderWithStoreAndRouter(state)

        renderWithStore(
            <I18nextProvider i18n={testI18n}>
                <UnlockKeyButton userEmailHash={exampleHashedEmail} />
            </I18nextProvider>
        )

        const button = screen.getByTestId(LOCK_KEY_BUTTON)
        fireEvent.mouseOver(button)

        const tooltip = await screen.findByTestId(LOCK_KEY_TOOLTIP)

        expect(button.getAttribute("aria-label")).toEqual("unlock-key-button.key-error-label")
        expect(tooltip.textContent).toEqual("unlock-key-button.key-error-tooltip")
    })

    it("should render pending status if master key status is pending", async () => {
        const masterKeyResult: AsyncOperationResult<{ password: string; emailHash: string }, string, Error> = {
            status: "PENDING",
            params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
        }
        const keyPairResult: AsyncOperationResult<string, KeyPair, Error> = {
            status: "FINISHED",
            params: exampleMasterKey,
            data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
        }
        const collectionKeyResult: AsyncOperationResult<
            { collectionId: string; privateKey: string },
            EncryptionKey,
            Error
        > = {
            status: "FINISHED",
            params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
            data: {
                collectionId: exampleCollectionId,
                version: exampleEncryptionKeyVersion,
                key: "secret-collection-key",
            },
        }

        const state = {
            users: { masterKey: masterKeyResult, keyPair: keyPairResult },
            collections: { encryptionKey: collectionKeyResult, activeCollection: exampleCollectionId },
        }
        // eslint-disable-next-line testing-library/render-result-naming-convention
        const renderWithStore = renderWithStoreAndRouter(state)

        renderWithStore(
            <I18nextProvider i18n={testI18n}>
                <UnlockKeyButton userEmailHash={exampleHashedEmail} />
            </I18nextProvider>
        )

        const button = screen.getByTestId(LOCK_KEY_BUTTON)

        expect(button.getAttribute("aria-label")).toEqual("unlock-key-button.key-pending-label")
    })

    it("should render pending status if key pair status is pending", async () => {
        const masterKeyResult: AsyncOperationResult<{ password: string; emailHash: string }, string, Error> = {
            status: "PENDING",
            params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
        }
        const keyPairResult: AsyncOperationResult<string, KeyPair, Error> = {
            status: "FINISHED",
            params: exampleMasterKey,
            data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
        }
        const collectionKeyResult: AsyncOperationResult<
            { collectionId: string; privateKey: string },
            EncryptionKey,
            Error
        > = {
            status: "FINISHED",
            params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
            data: {
                collectionId: exampleCollectionId,
                version: exampleEncryptionKeyVersion,
                key: "secret-collection-key",
            },
        }

        const state = {
            users: { masterKey: masterKeyResult, keyPair: keyPairResult },
            collections: { encryptionKey: collectionKeyResult, activeCollection: exampleCollectionId },
        }
        // eslint-disable-next-line testing-library/render-result-naming-convention
        const renderWithStore = renderWithStoreAndRouter(state)

        renderWithStore(
            <I18nextProvider i18n={testI18n}>
                <UnlockKeyButton userEmailHash={exampleHashedEmail} />
            </I18nextProvider>
        )

        const button = screen.getByTestId(LOCK_KEY_BUTTON)

        expect(button.getAttribute("aria-label")).toEqual("unlock-key-button.key-pending-label")
    })

    it("should render pending status if collection key decryption is in progress", async () => {
        const masterKeyResult: AsyncOperationResult<{ password: string; emailHash: string }, string, Error> = {
            status: "FINISHED",
            params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
            data: exampleMasterKey,
        }
        const keyPairResult: AsyncOperationResult<string, KeyPair, Error> = {
            status: "FINISHED",
            params: exampleMasterKey,
            data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
        }
        const collectionKeyResult: AsyncOperationResult<
            { collectionId: string; privateKey: string },
            EncryptionKey,
            Error
        > = {
            status: "PENDING",
            params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
        }

        const state = {
            users: { masterKey: masterKeyResult, keyPair: keyPairResult },
            collections: { encryptionKey: collectionKeyResult, activeCollection: exampleCollectionId },
        }
        // eslint-disable-next-line testing-library/render-result-naming-convention
        const renderWithStore = renderWithStoreAndRouter(state)

        renderWithStore(
            <I18nextProvider i18n={testI18n}>
                <UnlockKeyButton userEmailHash={exampleHashedEmail} />
            </I18nextProvider>
        )

        const button = screen.getByTestId(LOCK_KEY_BUTTON)

        expect(button.getAttribute("aria-label")).toEqual("unlock-key-button.key-pending-label")
    })

    it("should render locked status if master key status is not started", async () => {
        const masterKeyResult: AsyncOperationResult<{ password: string; emailHash: string }, string, Error> = {
            status: "NOT_STARTED",
        }
        const keyPairResult: AsyncOperationResult<string, KeyPair, Error> = {
            status: "FINISHED",
            params: exampleMasterKey,
            data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
        }
        const collectionKeyResult: AsyncOperationResult<
            { collectionId: string; privateKey: string },
            EncryptionKey,
            Error
        > = {
            status: "FINISHED",
            params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
            data: {
                collectionId: exampleCollectionId,
                version: exampleEncryptionKeyVersion,
                key: "secret-collection-key",
            },
        }

        const state = {
            users: { masterKey: masterKeyResult, keyPair: keyPairResult },
            collections: { encryptionKey: collectionKeyResult, activeCollection: exampleCollectionId },
        }
        // eslint-disable-next-line testing-library/render-result-naming-convention
        const renderWithStore = renderWithStoreAndRouter(state)

        renderWithStore(
            <I18nextProvider i18n={testI18n}>
                <UnlockKeyButton userEmailHash={exampleHashedEmail} />
            </I18nextProvider>
        )

        const button = screen.getByTestId(LOCK_KEY_BUTTON)
        fireEvent.mouseOver(button)

        const tooltip = await screen.findByTestId(LOCK_KEY_TOOLTIP)

        expect(button.getAttribute("aria-label")).toEqual("unlock-key-button.key-locked-label")
        expect(tooltip.textContent).toEqual("unlock-key-button.key-locked-tooltip")
    })

    it("should render locked status if key pair status is not started", async () => {
        const masterKeyResult: AsyncOperationResult<{ password: string; emailHash: string }, string, Error> = {
            status: "FINISHED",
            params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
            data: exampleMasterKey,
        }
        const keyPairResult: AsyncOperationResult<string, KeyPair, Error> = {
            status: "NOT_STARTED",
        }
        const collectionKeyResult: AsyncOperationResult<
            { collectionId: string; privateKey: string },
            EncryptionKey,
            Error
        > = {
            status: "FINISHED",
            params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
            data: {
                collectionId: exampleCollectionId,
                version: exampleEncryptionKeyVersion,
                key: "secret-collection-key",
            },
        }

        const state = {
            users: { masterKey: masterKeyResult, keyPair: keyPairResult },
            collections: { encryptionKey: collectionKeyResult, activeCollection: exampleCollectionId },
        }
        // eslint-disable-next-line testing-library/render-result-naming-convention
        const renderWithStore = renderWithStoreAndRouter(state)

        renderWithStore(
            <I18nextProvider i18n={testI18n}>
                <UnlockKeyButton userEmailHash={exampleHashedEmail} />
            </I18nextProvider>
        )

        const button = screen.getByTestId(LOCK_KEY_BUTTON)
        fireEvent.mouseOver(button)

        const tooltip = await screen.findByTestId(LOCK_KEY_TOOLTIP)

        expect(button.getAttribute("aria-label")).toEqual("unlock-key-button.key-locked-label")
        expect(tooltip.textContent).toEqual("unlock-key-button.key-locked-tooltip")
    })

    it("should render locked status if collection key is locked", async () => {
        const masterKeyResult: AsyncOperationResult<{ password: string; emailHash: string }, string, Error> = {
            status: "FINISHED",
            params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
            data: exampleMasterKey,
        }
        const keyPairResult: AsyncOperationResult<string, KeyPair, Error> = {
            status: "FINISHED",
            params: exampleMasterKey,
            data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
        }
        const collectionKeyResult: AsyncOperationResult<
            { collectionId: string; privateKey: string },
            EncryptionKey,
            Error
        > = { status: "NOT_STARTED" }

        const state = {
            users: { masterKey: masterKeyResult, keyPair: keyPairResult },
            collections: { encryptionKey: collectionKeyResult, activeCollection: exampleCollectionId },
        }
        // eslint-disable-next-line testing-library/render-result-naming-convention
        const renderWithStore = renderWithStoreAndRouter(state)

        renderWithStore(
            <I18nextProvider i18n={testI18n}>
                <UnlockKeyButton userEmailHash={exampleHashedEmail} />
            </I18nextProvider>
        )

        const button = screen.getByTestId(LOCK_KEY_BUTTON)
        fireEvent.mouseOver(button)

        const tooltip = await screen.findByTestId(LOCK_KEY_TOOLTIP)

        expect(button.getAttribute("aria-label")).toEqual("unlock-key-button.key-locked-label")
        expect(tooltip.textContent).toEqual("unlock-key-button.key-locked-tooltip")
    })

    it("should render locked status if key is unlocked for different collection than active", async () => {
        const masterKeyResult: AsyncOperationResult<{ password: string; emailHash: string }, string, Error> = {
            status: "FINISHED",
            params: { password: exampleUserPassword, emailHash: exampleHashedEmail },
            data: exampleMasterKey,
        }
        const keyPairResult: AsyncOperationResult<string, KeyPair, Error> = {
            status: "FINISHED",
            params: exampleMasterKey,
            data: { publicKey: examplePublicKey, privateKey: examplePrivateKey },
        }
        const collectionKeyResult: AsyncOperationResult<
            { collectionId: string; privateKey: string },
            EncryptionKey,
            Error
        > = {
            status: "FINISHED",
            params: { collectionId: exampleCollectionId, privateKey: examplePrivateKey },
            data: {
                collectionId: exampleCollectionId,
                version: exampleEncryptionKeyVersion,
                key: "secret-collection-key",
            },
        }

        const state = {
            users: { masterKey: masterKeyResult, keyPair: keyPairResult },
            collections: { encryptionKey: collectionKeyResult, activeCollection: "another-collection" },
        }
        // eslint-disable-next-line testing-library/render-result-naming-convention
        const renderWithStore = renderWithStoreAndRouter(state)

        renderWithStore(
            <I18nextProvider i18n={testI18n}>
                <UnlockKeyButton userEmailHash={exampleHashedEmail} />
            </I18nextProvider>
        )

        const button = screen.getByTestId(LOCK_KEY_BUTTON)
        fireEvent.mouseOver(button)

        const tooltip = await screen.findByTestId(LOCK_KEY_TOOLTIP)

        expect(button.getAttribute("aria-label")).toEqual("unlock-key-button.key-locked-label")
        expect(tooltip.textContent).toEqual("unlock-key-button.key-locked-tooltip")
    })

    it("should lock key on click when unlocked", () => {
        const lock = jest.fn()
        render(
            <UnlockKeyButtonRaw userEmailHash={exampleHashedEmail} status="Unlocked" t={tFunctionMock} lockKey={lock} />
        )
        const button = screen.getByTestId(LOCK_KEY_BUTTON)
        fireEvent.click(button)
        expect(lock).toHaveBeenCalledTimes(1)
    })

    it("should open password modal on click when locked", () => {
        const lock = jest.fn()
        render(
            <UnlockKeyButtonRaw userEmailHash={exampleHashedEmail} status="Locked" t={tFunctionMock} lockKey={lock} />
        )
        const button = screen.getByTestId(LOCK_KEY_BUTTON)
        fireEvent.click(button)

        const modal = screen.getByTestId(ENTER_PASSWORD_MODAL)
        expect(modal.textContent).toEqual("true")
    })

    it("should open password modal on click when failed", () => {
        const lock = jest.fn()
        render(
            <UnlockKeyButtonRaw userEmailHash={exampleHashedEmail} status="Failed" t={tFunctionMock} lockKey={lock} />
        )
        const button = screen.getByTestId(LOCK_KEY_BUTTON)
        fireEvent.click(button)

        const modal = screen.getByTestId(ENTER_PASSWORD_MODAL)
        expect(modal.textContent).toEqual("true")
    })

    it("should do nothing on click when pending", () => {
        const lock = jest.fn()
        render(
            <UnlockKeyButtonRaw userEmailHash={exampleHashedEmail} status="Pending" t={tFunctionMock} lockKey={lock} />
        )
        const button = screen.getByTestId(LOCK_KEY_BUTTON)
        fireEvent.click(button)

        const modal = screen.getByTestId(ENTER_PASSWORD_MODAL)

        expect(modal.textContent).toEqual("false")
        expect(lock).not.toHaveBeenCalled()
    })
})
