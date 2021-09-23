export const deleteFromSetBy = <T>(set: Set<T>, matcher: (elem: T) => boolean): Set<T> => {
    return new Set(Array.from(set).filter((elem) => !matcher(elem)))
}
