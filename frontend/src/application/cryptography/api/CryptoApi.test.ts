/**
 * @jest-environment node
 */

// See https://github.com/facebook/jest/issues/7780

import CryptoApi from "./CryptoApi"
import forge from "node-forge"

const examplePublicKey = `-----BEGIN PUBLIC KEY-----
MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA7qa66fPmvcuqxEKmBj10
q4E6bztkqNy56KazaTYv/DIpHToCltfUTnd5cF3OWoPZI3iCQfJWJq88kQMylX7q
NYD24HTZ664o22nCfJWT/dWO9almbaLxPf5JC38oGjGnaK5FuOCOmqGgnoaT00vt
WqmystZk24Xv62nYVXcY9/nlSPg0XCA5t7UsuR+3PXhkD7CnmR1ZyxM2kg0BtXqy
gMu0+QFqSBBzNq3ibqpziWCNVDnQ4AimdvX2NHtsnZIp5R/52lRYccIBBLyhbO0V
C8lG6mwlq+1K8sPRiqeGNUAMa0bAd5uGRbJzUg/VIzs8TdhNFdNmqH5Gnm7sJSwc
p+2frDws3KjNQ5DHZp10iibZCcH+ab/j5tWFf+yta85Mu+l5qfRma3ij9G+E+srA
kLRNJYQl7HH89wuodGkDNDLtX6nPoY1N14zBKeKhSaqzd/PCwMHyOknbrTEh4bqD
UUAF1NoErOE9q+VBkMAjwhNvcxPY4T6cUw3klS1h/meDAAAf+u+2vbVxzCihK4J5
wAisMs9zygqitch+avyUvfmf2HH67JjFywjkIqGV+FC94r6R5t5Cf+lADCR4Ab0X
XxaBpPF5HR2z8UmW8ZGg4gsNbCVDzmnzVZARRFY+16ooCVt3g8uLMxgjHpUxrJIe
jPzmrJgyYcX7cfPDWg3JbkkCAwEAAQ==
-----END PUBLIC KEY-----`

const examplePrivateKey = `-----BEGIN RSA PRIVATE KEY-----
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
-----END RSA PRIVATE KEY-----`

const exampleEncryptionInput = "FooBar ^& 123 łąć 🦫"

const exampleNonUtfEncryptionInput = "Baz Qux 135 !#%&"

describe("Crypto API", () => {
    describe("derive key", () => {
        it("should derive key using password and salt", async () => {
            const password = "secret-password"
            const salt = "password-salt"
            const result = await CryptoApi.deriveKey(password, salt)
            expect(result).toEqual("12d424724067e66bbfc80f0df651695792a42307e9507b2725600016c8dbc337")
        })
    })

    describe("generate key pair", () => {
        it("should generate public and private key", async () => {
            const result = await CryptoApi.generateKeyPair(4096)
            expect(result.publicKey).toMatch("-----BEGIN PUBLIC KEY-----")
            expect(result.privateKey).toMatch("-----BEGIN RSA PRIVATE KEY-----")
            expect(forge.pki.publicKeyFromPem(result.publicKey).n.bitLength()).toEqual(4096)
        })
    })

    describe("symmetric encrypt and decrypt", () => {
        it("should encrypt and decrypt string", async () => {
            const encryptionKey = "12d424724067e66bbfc80f0df651695792a42307e9507b2725600016c8dbc337"
            const encrypted = await CryptoApi.encryptString(exampleEncryptionInput, encryptionKey, true)
            const decrypted = await CryptoApi.decryptToString(encrypted, encryptionKey, true)

            expect(encrypted).toMatch(/^AES-CBC:[a-f0-9]+:[a-f0-9]+$/)
            expect(decrypted).toEqual(exampleEncryptionInput)
        })

        it("should encrypt and decrypt binary data", async () => {
            const encryptionKey = "12d424724067e66bbfc80f0df651695792a42307e9507b2725600016c8dbc337"
            const rawInput = new Uint8Array([5, 20, 70, 111, 111, 200, 0, 188, 5, 11, 122, 122])
            const buffer = rawInput.buffer
            const encrypted = await CryptoApi.encryptBinary(buffer, encryptionKey)
            const decrypted = await CryptoApi.decryptBinary(encrypted, encryptionKey)

            expect(encrypted).toMatch(/^AES-CBC:[a-f0-9]+:[a-f0-9]+$/)
            expect(decrypted).toEqual(rawInput)
        })

        it("should encrypt and decrypt string with non default chunk size", async () => {
            const encryptionKey = "12d424724067e66bbfc80f0df651695792a42307e9507b2725600016c8dbc337"
            const encrypted = await CryptoApi.encryptString(exampleEncryptionInput, encryptionKey, true, 2)
            const decrypted = await CryptoApi.decryptToString(encrypted, encryptionKey, true, 2)

            expect(encrypted).toMatch(/^AES-CBC:[a-f0-9]+:[a-f0-9]+$/)
            expect(decrypted).toEqual(exampleEncryptionInput)
        })

        it("should encrypt and decrypt binary data with non default chunk size", async () => {
            const encryptionKey = "12d424724067e66bbfc80f0df651695792a42307e9507b2725600016c8dbc337"
            const rawInput = new Uint8Array([5, 20, 70, 111, 111, 200, 0, 188, 5, 11, 122, 122])
            const buffer = rawInput.buffer
            const encrypted = await CryptoApi.encryptBinary(buffer, encryptionKey, 2)
            const decrypted = await CryptoApi.decryptBinary(encrypted, encryptionKey, 2)

            expect(encrypted).toMatch(/^AES-CBC:[a-f0-9]+:[a-f0-9]+$/)
            expect(decrypted).toEqual(rawInput)
        })

        it("should encrypt and decrypt non utf string", async () => {
            const encryptionKey = "12d424724067e66bbfc80f0df651695792a42307e9507b2725600016c8dbc337"
            const encrypted = await CryptoApi.encryptString(exampleNonUtfEncryptionInput, encryptionKey, false)
            const decrypted = await CryptoApi.decryptToString(encrypted, encryptionKey, false)

            expect(encrypted).toMatch(/^AES-CBC:[a-f0-9]+:[a-f0-9]+$/)
            expect(decrypted).toEqual(exampleNonUtfEncryptionInput)
        })

        it("should return proper error if algorithm is missing in input during decryption", async () => {
            const encryptionKey = "12d424724067e66bbfc80f0df651695792a42307e9507b2725600016c8dbc337"
            const encryptedInput =
                ":bf66d5f956162efdf7ec67d158378605:5b596454dec31ee65fbbec8ef11f5a266227d35572c124461260b5909904ba41e2b43b6114ae351fa6d2181c9313bac8"

            await expect(CryptoApi.decryptToString(encryptedInput, encryptionKey, true)).rejects.toEqual(
                new Error("Malformed encrypted input")
            )
        })

        it("should return proper error if iv is missing in input during decryption", async () => {
            const encryptionKey = "12d424724067e66bbfc80f0df651695792a42307e9507b2725600016c8dbc337"
            const encryptedInput =
                "AES-CBC::5b596454dec31ee65fbbec8ef11f5a266227d35572c124461260b5909904ba41e2b43b6114ae351fa6d2181c9313bac8"

            await expect(CryptoApi.decryptToString(encryptedInput, encryptionKey, true)).rejects.toEqual(
                new Error("Malformed encrypted input")
            )
        })

        it("should return proper error if ciphertext is missing in input during decryption", async () => {
            const encryptionKey = "12d424724067e66bbfc80f0df651695792a42307e9507b2725600016c8dbc337"
            const encryptedInput = "AES-CBC:bf66d5f956162efdf7ec67d158378605:"

            await expect(CryptoApi.decryptToString(encryptedInput, encryptionKey, true)).rejects.toEqual(
                new Error("Malformed encrypted input")
            )
        })

        it("should return proper error if algorithm in input is not supported during decryption", async () => {
            const encryptionKey = "12d424724067e66bbfc80f0df651695792a42307e9507b2725600016c8dbc337"
            const encryptedInput =
                "FooBar:bf66d5f956162efdf7ec67d158378605:5b596454dec31ee65fbbec8ef11f5a266227d35572c124461260b5909904ba41e2b43b6114ae351fa6d2181c9313bac8"

            await expect(CryptoApi.decryptToString(encryptedInput, encryptionKey, true)).rejects.toEqual(
                new Error("FooBar is not valid cipher")
            )
        })
    })

    describe("asymmetric encrypt and decrypt", () => {
        it("should encrypt and decrypt string", async () => {
            const encrypted = await CryptoApi.asymmetricEncrypt(exampleEncryptionInput, examplePublicKey)
            const decrypted = await CryptoApi.asymmetricDecrypt(encrypted, examplePrivateKey)

            expect(encrypted).toMatch(/^[a-f0-9]+$/)
            expect(decrypted).toEqual(exampleEncryptionInput)
        })

        it("should return error on invalid key", async () => {
            const invalidPrivateKey = `-----BEGIN RSA PRIVATE KEY-----
            MIIJKQIBAAKCAgEA4HZgyNHB1n/NjTywWtoY9qk5mds5S821uMr+HoghorXn+/3Q
            aFopXXHlXAxnJkG6ozgN+gBCALdHrtLIG2AaqwO/v0nLR/IevD2o0xwLXedWQB+h
            iDpZgS/FejbxVI2HiqrIgUE00+XBnN+ru5TlNM8pCWeRiDbvVWWYDUuoggFpnlAD
            Nj+kms6GopAQ0TbIpgVGiddDU8xKh+0hbLGgyAFl/EluidEU0AWO7kNZFN/wve2b
            WnySsO2SWMvfabqv1tRMxhactCj8TJAtfVPr385Rtt/7+pZt3L/qxkIT8StNZitV
            wqIVd/0tMQ2ufrRSrHf6pbJ6EKooPjTYurtw43wZs3vKeZ0+qiAlbp0W6OtKDtYq
            ABCZLZc+GUFD1mwEMO3LrHo9Vw64Ymi5yian6hE6k/49D+ZzBY4k/X4IuCL+nbtm
            hNM33EE3mmUWmlpClUqC24ozf3iZO/qdyBBzwhsb0zS+nQPy6MyNGKUWl4WZCzfc
            xWeJkn80ps30yXNAQ2S20tedYgcfmz88SbdXEIns9wb+qoVbPmdD2af3gItqpD2n
            VT6U7GOw/Y2hINqwApKZfkrCXVaJBrYlrILc1KGse4yz+coISs8jFmQO7giAI08M
            CU5x5QPw/AjsNUeBYEya7sp6BOYOs0m2X5RlAgje/gT1IZhtETHnoEG/7N0CAwEA
            AQKCAgEAxwSu89/nukQ/AMeapjNShwGsbNqPhsKyKVLy1idli1s2gZueOagXnR7r
            eh4VJzqIPUfqPIwV+YSILl39xygC/TapwPWxI+1tiwIwul3XRj9jnh9tndf+nCi7
            M42blJiYYT7j4JbsPCUu8eTv47Y1NAmqHQiTZO8wbctSchdvJTYTa6cN8f+j9w6/
            GUwbXSTx6yIqVPVkYNx2COccB7/HCw8SWgrR06hqh7cl7+QPDA8y6XPPlv91ISLD
            6694O8qnNheuqsRJ1QOyEECV1NWgAiRxF3Cpfpn6dwxDId5cyCzYx4oM1DHaTzVP
            6Hp1CSrhUsJenqQMztHQNLeuooKnp6IZGajPcCmQQ5p0FyD22eSBFvg8kP5kKJbd
            7p9YxTOTAbIQb0snRZ3aIypP2vV9MarqL2JPVNeSfYDM/VZ7laXE4YnKKfYJv/QC
            Pjpkz9Ecd8l5IQ60d0ayKYQWkLlx/uHA8nz/g/+knuphWwam92nzwrzSTtxQRLE6
            XQV0V0wA7pCG9iBXKL0Wr6o8BuFAs8rUEtXZSsoHUnGqNSL3l4IHk7WzBM1akV3e
            /8JFWcw5JZLBzlGBe67vCnr6kKk6piDOZJYWANIlFExWbSmQgJOsiqK7OHmxiIJU
            WqkKzuKNb0yPz/p6kxEPRUxCKIkzdVqpxXMifMlmblZBsAEBcZECggEBAP1RbaTC
            CAwu8nYrfOUlxaEQqQCNOBFuJU5Ptp3dfWWE7hyP0pT5fL8AzEqtH+2G1M9tJa56
            QTU+sa2Yfy4LgdcTyLJw8ymtX4v53JifXDS8MPy6HM2+YhfDGqTDrzENwA3cVduX
            po6doPbjrpf0d1mM4LSWJKOb9CL8MfAdxn9Y///kCAy0FGPwJrs/ss7mzQmOPtqa
            bz/7drNZMKPyL9Z9P6DpXHVfi7XXZqpqbg25kgcKRwU9G/N9ACP+SB71O+P84WUI
            A8vm54ma6sjJ1aqZDAsqGviYwXmM/x4sxZ88UQycQrxG/V5QwFLNJxh8+GPdfugQ
            ga7UM6I5WvAKGesCggEBAOLWvekJx1SLWcuZ5Tg5V3j7FmMRsz4BHP7V78CjUZZU
            c2E7/HRvdvgeEtQuTOgnYMAwz5W1szlJhclq00WtDCNN4/eMt5Erpwd1GdrnGYlW
            NVTqzEVXXAeJoXCjN7GxE+wDU2qE3i2tiitpNbZ+NgUNiZ47xiYAHh2hTLS9EW/S
            oj+iLZGrEu+vwp0imJOSMyHhJx2Eh87RuWOb0rR848IYYkfO000z5yvHGKuuRA4H
            b/JIbBI5BA8rkD7ZL4qtSX7gqBwZnK6TIRxssMLXpqGe2L6GppK0/FTZnOL4SUUS
            dSa6b5nGWHQnO3cHKFO+vLsyPU6WoOHYw+rt4KX92lcCggEAF9JXgD3q6dJkZK50
            QUxLCHl7mxysoB7+jR27656Pk0Nr7O2tfOr1SzT1ATTEot6iFpuKBp1iknJB3TRR
            CXB1llc68WTjyRCPwqcNkqCx2Be2d5285bA4o0lqsQHh8Lf9TpBg1pStSg4SvATv
            P0TzEE3KN9FCwswAqmWTAKScLQ+ei17TTvaEUF2eFs/HreNQdsyUppQNUDQClp0V
            kOVOkFoY6LXpRvRkPAZ0liQKE2pXDChwQ1La1Qv1Pm1ENP0U07zNKWYWjU86iNsz
            pwwr9q4LzuT3wlcDIr4ao6jMMMxIRupO8EuwufTYETFPsuLVrPaAX6xltZ4o+OFx
            5s7cXwKCAQEAkDZX3E0ENcK2HtPDP6Bs9RQkYm2BV90bUwQt/XmXSAx+ogWh9WBb
            8VglPlIFQN6wCt+9SX+P5s7QSIIquTRP8C33zYzfs2JeOUaAXoPn30E4fLkdjsQA
            VSZxxYtuwABFGxnuMV0xZzbaDclAhbntm/7ETB3SVpxiiRHgE1PUPxRZZD013VM2
            ob1K691vxPoAmfxrgr+cG6eHxkTtiyOPJPmdmetquL1TrahRDeynhYfC6vSSCG/J
            Hd/jP6GSMQcEH66yEdNSokYVmd2b7vNtE3lKcK5oPgLkHQmQTVfuNQSMCEgk5U2N
            NKMwkfax87Pihtxkz47Bz3Og8r2ywbUG1wKCAQA4WwW3XlUP5RqWxWh0R1pAAv5/
            L/z6jvz/uRND2MOc4Hd/9yzSQbAZinKPg4KJ3pFmcBuRnPr8qL5bCxZXC6xrjcch
            igvQX04ZpWEudeKRD4xsYRzKJAKxFy32eV2Eq8ryK8y/4rgATRoJeICu9PpssVRk
            zB267ELcb1mQHUJt6ZsHDIbGZINxiq8RM2wLqAkp5JDLe8VYdzTyirYqGH63JH1y
            fG0yH899G7dA0zKt4Ajjhc8DAxJJy2Q3/LJRkOLbLMwwGZrtRTE9L0bxS5TdHDCT
            lRiv54O4koLE9jduA4PSVkXxAS2d9JJyWhO+SL08zqG1oi8GD3Z8/oeWBRxn
            -----END RSA PRIVATE KEY-----`

            const encrypted = await CryptoApi.asymmetricEncrypt(exampleEncryptionInput, examplePublicKey)
            await expect(CryptoApi.asymmetricDecrypt(encrypted, invalidPrivateKey)).rejects.toThrow(/is invalid\.$/)
        })
    })

    describe("Random bytes", () => {
        it("should draw bytes with proper length", async () => {
            const result = await CryptoApi.randomBytes(18)
            expect(result).toMatch(/^[a-f0-9]{36}$/)
        })
    })

    describe("digest", () => {
        it("should compute string digest", async () => {
            const result = await CryptoApi.digest(exampleEncryptionInput)
            expect(result).toEqual("2e6bc4291fbe3a9420bd359cb9e7c9ec015e1a5704282448dd2fd376c8168974")
        })

        it("should compute string digest with non default chunk size", async () => {
            const result = await CryptoApi.digest(exampleEncryptionInput, 2)
            expect(result).toEqual("2e6bc4291fbe3a9420bd359cb9e7c9ec015e1a5704282448dd2fd376c8168974")
        })
    })
})
