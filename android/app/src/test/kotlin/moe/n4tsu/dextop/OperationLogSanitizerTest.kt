package moe.n4tsu.dextop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationLogSanitizerTest {
    @Test
    fun removesSensitiveIdentifiersFromWindowDiagnostics() {
        val sanitized = OperationLog.sanitizeForReport(
            "component=com.example.private/.SecretActivity " +
                "owner=user@example.com uri=content://com.example.provider/private/42 " +
                "dump=/data/user/0/com.example.private/files/state.json"
        )

        assertFalse(sanitized.contains("com.example.private"))
        assertFalse(sanitized.contains("SecretActivity"))
        assertFalse(sanitized.contains("user@example.com"))
        assertFalse(sanitized.contains("content://"))
        assertFalse(sanitized.contains("/data/user/0"))
        assertTrue(sanitized.contains("<component>") || sanitized.contains("<redacted>"))
        assertTrue(sanitized.contains("<email>"))
        assertTrue(sanitized.contains("<uri>") || sanitized.contains("uri=<redacted>"))
        assertTrue(sanitized.contains("<path>"))
    }
}
