import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import LoadingOverlay from "../../../../../application/components/common/LoadingOverlay"
import { AppState } from "../../../../../application/store"
import { fetchMultipleUsersPublicDataAction } from "../../../../user/store/Actions"
import { requestPermissionsChangeForMemberAction } from "../../../store/Actions"
import AddOrUpdateMemberModal from "./AddOrUpdateMemberModal"

type Props = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps> & Pick<WithTranslation, "t">

const EditMemberPermissionsContainer: React.FC<Props> = ({
    requestedUpdate,
    unsetUpdateRequest,
    knownUsers,
    fetchUserData,
    t,
}) => {
    const userResult = knownUsers[requestedUpdate?.memberId ?? ""]
    const userData = userResult?.status === "FINISHED" ? userResult.data : undefined

    useEffect(() => {
        if (!requestedUpdate || userData || userResult.status === "PENDING") {
            return
        }
        fetchUserData(requestedUpdate.memberId)
    })

    if (!requestedUpdate) {
        return null
    } else if (userResult.status === "PENDING" || userResult.status === "NOT_STARTED") {
        return <LoadingOverlay active={true} text={t("common:action-in-progress")} />
    } else if (userData) {
        return (
            <AddOrUpdateMemberModal
                isOpen={true}
                onClose={unsetUpdateRequest}
                collection={requestedUpdate.collection}
                user={userData}
                operationProps={{ currentPermissions: requestedUpdate.currentPermissions }}
            />
        )
    }
    return null
}

const mapStateToProps = (state: AppState) => ({
    knownUsers: state.users.knownUsers,
    requestedUpdate: state.collections.requestedPermissionUpdateForMember,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    fetchUserData: (memberId: string) => dispatch(fetchMultipleUsersPublicDataAction.started([memberId])),
    unsetUpdateRequest: () => dispatch(requestPermissionsChangeForMemberAction(null)),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(EditMemberPermissionsContainer))
