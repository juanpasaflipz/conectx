package app.conectx.transport

import app.conectx.domain.model.Peer

/** Represents an active connection to a peer via any transport. */
data class Connection(
    val peer: Peer,
    val endpointId: String
)
