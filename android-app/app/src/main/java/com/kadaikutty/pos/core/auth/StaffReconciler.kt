package com.kadaikutty.pos.core.auth

// The server's view of one staff account (GET /staff), independent of any JSON/Android framework
// type so the reconciliation plan below is unit-testable in isolation.
data class ServerStaffRecord(
    val userId: String,
    val phone: String,
    val displayName: String,
    val role: String,
    val permissions: String,
    val status: String,
    val isCloudTier: Boolean = true,
    val cloudAccessGrantedUntilEpochMs: Long? = null,
)

// What StaffReconciler.plan() decided a local Room row needs, kept separate from actually
// executing it (that part needs a real transactional BillingDatabase and isn't unit-testable).
sealed interface StaffReconciliationAction {
    // No active server account matches this local row's phone: it's an orphan (deactivated/
    // rejected server-side, or a leftover from before staff creation was made backend-first).
    data class Delete(val localId: String) : StaffReconciliationAction

    // Same phone, different id: the pre-fix "local id != server id" bug. Re-key the local row (and
    // its stored offline credential) to the server's canonical id, keeping this device's existing
    // salt/verifier so offline login for this device keeps working.
    data class Rekey(val local: UserEntity, val server: ServerStaffRecord) : StaffReconciliationAction

    // Same id, but display name or permissions drifted: bring the local row back in line.
    data class UpdateFields(val local: UserEntity, val server: ServerStaffRecord) : StaffReconciliationAction

    // An active server account has no local mirror at all: insert a placeholder (blank salt/
    // verifier -- offline login is unavailable until this staff member signs in once online on
    // this device, same as any brand-new staff member today).
    data class InsertMissing(val server: ServerStaffRecord, val companyId: String) : StaffReconciliationAction
}

// Diffs the server's authoritative staff list (GET /staff) against this device's local mirror and
// decides what needs fixing. Pure function, no I/O, so the "self-heal" logic is testable without a
// real database or network — see StaffReconcilerTest.
object StaffReconciler {
    fun normalizePhone(raw: String): String = raw.filter(Char::isDigit).takeLast(10)

    fun plan(localUsers: List<UserEntity>, serverStaff: List<ServerStaffRecord>, adminUserId: String, companyId: String): List<StaffReconciliationAction> {
        val activeServer = serverStaff.filter { it.status == "ACTIVE" }
        val serverByPhone = activeServer.associateBy { normalizePhone(it.phone) }
        val actions = mutableListOf<StaffReconciliationAction>()

        for (local in localUsers) {
            if (local.id == adminUserId) continue // never touch the caller's own row
            val match = serverByPhone[normalizePhone(local.username)]
            when {
                match == null -> actions += StaffReconciliationAction.Delete(local.id)
                match.userId != local.id -> actions += StaffReconciliationAction.Rekey(local, match)
                local.displayName != match.displayName || local.permissions != match.permissions ||
                    local.isCloudTier != match.isCloudTier || local.cloudAccessGrantedUntilEpochMs != match.cloudAccessGrantedUntilEpochMs ->
                    actions += StaffReconciliationAction.UpdateFields(local, match)
            }
        }

        val localPhones = localUsers.map { normalizePhone(it.username) }.toSet()
        for (server in activeServer) {
            val normalized = normalizePhone(server.phone)
            if (normalized.isNotBlank() && normalized !in localPhones) {
                actions += StaffReconciliationAction.InsertMissing(server, companyId)
            }
        }
        return actions
    }
}
