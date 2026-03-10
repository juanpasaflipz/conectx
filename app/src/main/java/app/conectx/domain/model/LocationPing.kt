package app.conectx.domain.model

data class LocationPing(
    val section: String,
    val row: String?,
    val seat: String?,
    val note: String?,
    val battery: Int
)
