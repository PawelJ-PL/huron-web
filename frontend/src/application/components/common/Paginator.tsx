import { Box, BoxProps, Button, ButtonProps, IconButton, Stack, useBreakpointValue } from "@chakra-ui/react"
import React from "react"
import { Pagination } from "../../api/Pagination"
import { HiOutlineChevronLeft } from "react-icons/hi"
import { HiOutlineChevronRight } from "react-icons/hi"
import { WithTranslation, withTranslation } from "react-i18next"
import { booleanResponsiveConfig, BreakPoint, numberResponsiveConfig } from "../../utils/responsive"

type Props = {
    pagination: Pagination<unknown>
    onPageChange: (n: number) => void
    siblings?: number | Record<BreakPoint, number>
    showArrows?: boolean | Record<BreakPoint, boolean>
    containerProps?: BoxProps
    buttonProps?: Omit<ButtonProps, "onClick">
} & Pick<WithTranslation, "t">

const DEFAULT_SIBLINGS = { base: 1 }
const DEFAULT_SHOW_ARROWS = { base: false, sm: true }

const Paginator: React.FC<Props> = ({
    t,
    pagination,
    onPageChange,
    siblings,
    showArrows,
    containerProps,
    buttonProps,
}) => {
    const siblingsValues = numberResponsiveConfig(siblings, DEFAULT_SIBLINGS)
    const siblingsNumber = useBreakpointValue(siblingsValues) ?? DEFAULT_SIBLINGS.base

    const showArrowsConfig = booleanResponsiveConfig(showArrows, DEFAULT_SHOW_ARROWS)
    const shouldShowArrows = useBreakpointValue(showArrowsConfig) ?? DEFAULT_SHOW_ARROWS.base

    const leftSiblings = Array.from(Array(siblingsNumber).keys())
        .map((i) => pagination.page - (i + 1))
        .filter((n) => n > 1)
        .reverse()

    const rightSiblings = Array.from(Array(siblingsNumber).keys())
        .map((i) => pagination.page + (i + 1))
        .filter((n) => n < pagination.totalPages)

    const renderButton = (page: number, active?: boolean) => (
        <Button
            key={page}
            colorScheme="brand"
            variant={active ? "outline" : "solid"}
            size="sm"
            {...(buttonProps ?? {})}
            onClick={() => (page === pagination.page ? () => undefined : onPageChange(page))}
        >
            {page}
        </Button>
    )

    const renderPlaceholder = () => <Box alignSelf="flex-end">...</Box>

    return (
        <Stack direction="row" {...(containerProps ?? {})}>
            {shouldShowArrows && (
                <IconButton
                    aria-label={t("common:pagination.previous-page")}
                    icon={<HiOutlineChevronLeft />}
                    size="sm"
                    colorScheme="brand"
                    onClick={() => onPageChange(pagination.prevPage ?? 1)}
                    isDisabled={!pagination.prevPage}
                />
            )}
            {pagination.page !== 1 && renderButton(1)}
            {pagination.page - siblingsNumber > 2 && renderPlaceholder()}
            {leftSiblings.map((n) => renderButton(n))}
            {renderButton(pagination.page, true)}
            {rightSiblings.map((n) => renderButton(n))}
            {pagination.page + siblingsNumber + 1 < pagination.totalPages && renderPlaceholder()}
            {pagination.totalPages > pagination.page && renderButton(pagination.totalPages)}
            {shouldShowArrows && (
                <IconButton
                    aria-label={t("common:pagination.next-page")}
                    icon={<HiOutlineChevronRight />}
                    size="sm"
                    colorScheme="brand"
                    onClick={() => onPageChange(pagination.nextPage ?? pagination.totalPages)}
                    isDisabled={!pagination.nextPage}
                />
            )}
        </Stack>
    )
}

export default withTranslation()(Paginator)
