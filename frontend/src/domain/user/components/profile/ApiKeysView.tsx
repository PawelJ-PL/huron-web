import React, { useEffect } from "react"
import { WithTranslation, withTranslation } from "react-i18next"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import AlertBox from "../../../../application/components/common/AlertBox"
import Loader from "../../../../application/components/common/Loader"
import { AppState } from "../../../../application/store"
import { fetchApiKeysAction, resetFetchApiKeysStatusAction } from "../../store/Actions"
import capitalize from "lodash/capitalize"
import ApiKeyList from "./ApiKeysList"

type Props = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps> & Pick<WithTranslation, "t">

export const ApiKeysView: React.FC<Props> = ({ apiKeys, fetchKeys, resetKeysResult, t }) => {
    useEffect(() => {
        if (apiKeys.status !== "PENDING") {
            fetchKeys()
        }
        return () => {
            resetKeysResult()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    if (apiKeys.status === "PENDING" || apiKeys.status === "NOT_STARTED") {
        return <Loader title={t("profile-page:loading-api-keys")} />
    } else if (apiKeys.status === "FAILED") {
        return <AlertBox title={capitalize(t("common:unexpected-error"))} status="error" />
    } else {
        return <ApiKeyList apiKeys={apiKeys.data} />
    }
}

const mapStateToProps = (state: AppState) => ({
    apiKeys: state.users.apiKeys,
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
    fetchKeys: () => dispatch(fetchApiKeysAction.started()),
    resetKeysResult: () => dispatch(resetFetchApiKeysStatusAction()),
})

export default connect(mapStateToProps, mapDispatchToProps)(withTranslation()(ApiKeysView))
