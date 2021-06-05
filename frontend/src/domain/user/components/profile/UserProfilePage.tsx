import { useToast } from "@chakra-ui/toast"
import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import Loader from "../../../../application/components/common/Loader"
import { AppState } from "../../../../application/store"
import {
    refreshUserDataAction,
    resetRefreshUserDataStatusAction,
    resetUpdateUserDataStatusAction,
} from "../../store/Actions"
import UserProfileView from "./UserProfileView"

const REFRESH_FAILED_TOAST_ID = "refresh-data-failed"

type Props = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps> & Pick<WithTranslation, "t">

export const UserProfilePage: React.FC<Props> = ({
    resetUpdateProfileState,
    refreshData,
    resetRefreshStatus,
    t,
    refreshDataStatus,
    userData,
}) => {
    useEffect(() => {
        resetRefreshStatus()
        resetUpdateProfileState()
        refreshData()
        return () => {
            resetRefreshStatus()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const toast = useToast({ position: "top" })

    useEffect(() => {
        if (refreshDataStatus === "FAILED" && !toast.isActive(REFRESH_FAILED_TOAST_ID)) {
            toast({
                isClosable: true,
                duration: 7000,
                id: REFRESH_FAILED_TOAST_ID,
                status: "warning",
                title: t("profile-page:refresh-failed-toast.title"),
                description: t("profile-page:refresh-failed-toast.description"),
            })
        }
    })

    if (refreshDataStatus === "PENDING") {
        return <Loader title={t("profile-page:loading-user-data")} />
    } else if (["FINISHED", "FAILED"].includes(refreshDataStatus) && userData) {
        return <UserProfileView userData={userData} />
    } else {
        return null
    }
}

const mapStateToProps = (state: AppState) => ({
    refreshDataStatus: state.users.refreshUserDataStatus.status,
    userData: state.users.userData.status === "FINISHED" ? state.users.userData.data : undefined,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    resetUpdateProfileState: () => dispatch(resetUpdateUserDataStatusAction()),
    refreshData: () => dispatch(refreshUserDataAction.started()),
    resetRefreshStatus: () => dispatch(resetRefreshUserDataStatusAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(UserProfilePage))
