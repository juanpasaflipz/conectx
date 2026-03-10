package app.conectx.transport

import app.conectx.domain.model.Peer

data class Connection(
    val peer: Peer,
    val endpointId: String
)
