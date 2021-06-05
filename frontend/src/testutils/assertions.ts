export function assertHttpErrorWithStatusCode(result: Promise<unknown>, expectedCode: number): Promise<void> {
    return expect(result).rejects.toMatchObject({
        name: "HTTPError",
        response: {
            status: expectedCode,
        },
    })
}
