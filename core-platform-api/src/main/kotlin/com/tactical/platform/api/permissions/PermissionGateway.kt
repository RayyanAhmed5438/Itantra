package com.tactical.platform.api.permissions

interface PermissionGateway {

    /**
     * Requests the given permission, suspending until the user responds
     * (or until the OS auto-denies, e.g. if the permission was previously
     * denied with "don't ask again"). Returns true if granted.
     */
    suspend fun request(permission: Permission): Boolean
}
