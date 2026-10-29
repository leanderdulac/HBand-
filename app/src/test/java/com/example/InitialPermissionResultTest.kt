package com.example

import org.junit.Assert.*
import org.junit.Test

class InitialPermissionResultTest {
    @Test fun empty_result_is_not_a_grant() { assertFalse(allRequestedPermissionsGranted(emptyMap())) }
    @Test fun partial_denial_is_not_a_grant() {
        assertFalse(allRequestedPermissionsGranted(mapOf("scan" to true, "connect" to false)))
    }
    @Test fun nonempty_all_granted_remains_accepted() {
        assertTrue(allRequestedPermissionsGranted(mapOf("scan" to true, "notifications" to true)))
    }
}
