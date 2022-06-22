import { Heading, Link, Switch, Td, Tr } from "@chakra-ui/react"
import React from "react"
import { connect } from "react-redux"
import { Dispatch } from "redux"
import Author from "../../../../file/components/metadata_view/file/Author"
import { changeInvitationAcceptanceAction } from "../../../store/Actions"
import { Collection } from "../../../types/Collection"
import { Link as RouterLink } from "react-router-dom"

type Props = { collection: Collection } & ReturnType<typeof mapDispatchToProps>

const CollectionListEntry: React.FC<Props> = ({ collection, changeAcceptance }) => (
    <>
        <Tr>
            <Td>
                <Switch
                    colorScheme="brand"
                    isChecked={collection.isAccepted}
                    onChange={(event) => changeAcceptance(collection.id, event.target.checked)}
                />
            </Td>
            <Td>
                <Link as={RouterLink} to={`/collection/${collection.id}/manage`}>
                    <Heading as="h6" size="xs">
                        {collection.name}
                    </Heading>
                </Link>
            </Td>
            <Td>
                <Author authorId={collection.owner} />
            </Td>
        </Tr>
    </>
)

const mapDispatchToProps = (dispatch: Dispatch) => ({
    changeAcceptance: (collectionId: string, isAccepted: boolean) =>
        dispatch(changeInvitationAcceptanceAction.started({ collectionId, isAccepted })),
})

export default connect(undefined, mapDispatchToProps)(CollectionListEntry)
