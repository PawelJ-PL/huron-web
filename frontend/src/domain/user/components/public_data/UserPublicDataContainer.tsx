import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { FaUsersSlash } from "react-icons/fa"
import { connect } from "react-redux"
import { useParams } from "react-router-dom"
import { Dispatch } from "redux"
import EmptyPlaceholder from "../../../../application/components/common/EmptyPlaceholder"
import Loader from "../../../../application/components/common/Loader"
import UnexpectedErrorMessage from "../../../../application/components/common/UnexpectedErrorMessage"
import { AppState } from "../../../../application/store"
import { fetchUserPublicDataAction } from "../../store/Actions"
import UserPublicDataPage from "./UserPublicDataPage"

type Props = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps> & Pick<WithTranslation, "t">

export const UserPublicDataContainer: React.FC<Props> = ({ userDataResult, fetchUserPublicData, meDataResult, t }) => {
    const { userId = "" } = useParams<{ userId: string }>()

    useEffect(() => {
        if (
            (userDataResult.status === "FINISHED" || userDataResult.status === "PENDING") &&
            userDataResult.params === userId
        ) {
            return
        }
        fetchUserPublicData(userId)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    if (meDataResult.status !== "FINISHED") {
        return <UnexpectedErrorMessage error={new Error("Current user data not available")} />
    } else if (userDataResult.status === "FAILED") {
        return <UnexpectedErrorMessage error={userDataResult.error} />
    } else if (userDataResult.status === "FINISHED") {
        if (userDataResult.data === null) {
            return <EmptyPlaceholder icon={FaUsersSlash} text={t("user-public-page:user-not-found-message")} />
        } else {
            return <UserPublicDataPage userPublicData={userDataResult.data} self={meDataResult.data.id === userId} />
        }
    } else {
        return <Loader title={t("user-public-page:loading-user-data")} />
    }
}

const mapStateToProps = (state: AppState) => ({
    userDataResult: state.users.publicData,
    meDataResult: state.users.userData,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    fetchUserPublicData: (userId: string) => dispatch(fetchUserPublicDataAction.started(userId)),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(UserPublicDataContainer))
