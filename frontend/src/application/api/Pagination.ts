export type Pagination<T> = {
    result: T
    page: number
    elementsPerPage: number
    totalPages: number
    prevPage?: number
    nextPage?: number
}

export type PaginationRequest = {
    page?: number
    limit?: number
}
