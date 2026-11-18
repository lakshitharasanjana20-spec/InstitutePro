package com.example.institutepro.data.cloud

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val activeRole: String = "super_admin",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    companion object {
        val ALLOWED_ROLES = setOf("super_admin", "branch_manager", "teacher", "finance", "other", "student")

        fun sanitizeRole(role: String): String =
            if (role in ALLOWED_ROLES) role else "super_admin"

        fun toCreateMap(
            uid: String,
            displayName: String,
            email: String?,
            photoUrl: String?,
            activeRole: String
        ): Map<String, Any> {
            val raw = mapOf<String, Any?>(
                "userId" to uid,
                "displayName" to displayName.trim().ifBlank { "InstitutePro User" }.take(100),
                "email" to email?.trim()?.takeIf { it.isNotEmpty() }?.take(160),
                "photoUrl" to photoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(500),
                "activeRole" to sanitizeRole(activeRole),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            @Suppress("UNCHECKED_CAST")
            return raw.filterValues { it != null } as Map<String, Any>
        }

        fun toUpdateMap(
            displayName: String,
            email: String?,
            photoUrl: String?,
            activeRole: String
        ): Map<String, Any> {
            val raw = mapOf<String, Any?>(
                "displayName" to displayName.trim().ifBlank { "InstitutePro User" }.take(100),
                "email" to email?.trim()?.takeIf { it.isNotEmpty() }?.take(160),
                "photoUrl" to photoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(500),
                "activeRole" to sanitizeRole(activeRole),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            @Suppress("UNCHECKED_CAST")
            return raw.filterValues { it != null } as Map<String, Any>
        }
    }
}

@Keep
@IgnoreExtraProperties
data class CloudStudent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val studentCode: String = "",
    val fullName: String = "",
    val gender: String = "Male",
    val branchName: String = "",
    val status: String = "active",
    val phone: String = "",
    val registeredAt: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "studentCode" to studentCode.ifBlank { "STU" }.take(40),
        "fullName" to fullName.ifBlank { "Student" }.take(120),
        "gender" to if (gender.equals("Female", true)) "Female" else "Male",
        "branchName" to branchName.ifBlank { "Main" }.take(80),
        "status" to if (status == "inactive") "inactive" else "active",
        "phone" to phone.ifBlank { "0000000000" }.take(40),
        "registeredAt" to registeredAt.ifBlank { "2026-01-01" }.take(20),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "studentCode" to studentCode.ifBlank { "STU" }.take(40),
        "fullName" to fullName.ifBlank { "Student" }.take(120),
        "gender" to if (gender.equals("Female", true)) "Female" else "Male",
        "branchName" to branchName.ifBlank { "Main" }.take(80),
        "status" to if (status == "inactive") "inactive" else "active",
        "phone" to phone.ifBlank { "0000000000" }.take(40),
        "registeredAt" to registeredAt.ifBlank { "2026-01-01" }.take(20),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class CloudPayment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val receiptNo: String = "",
    val studentName: String = "",
    val studentCode: String = "",
    val amount: Double = 0.0,
    val method: String = "cash",
    val paidOn: String = "",
    val voided: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "receiptNo" to receiptNo.ifBlank { "RCP" }.take(60),
        "studentName" to studentName.ifBlank { "Student" }.take(120),
        "studentCode" to studentCode.ifBlank { "STU" }.take(40),
        "amount" to amount.coerceAtLeast(0.0),
        "method" to method.ifBlank { "cash" }.take(40),
        "paidOn" to paidOn.ifBlank { "2026-01-01" }.take(20),
        "voided" to voided,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "receiptNo" to receiptNo.ifBlank { "RCP" }.take(60),
        "studentName" to studentName.ifBlank { "Student" }.take(120),
        "studentCode" to studentCode.ifBlank { "STU" }.take(40),
        "amount" to amount.coerceAtLeast(0.0),
        "method" to method.ifBlank { "cash" }.take(40),
        "paidOn" to paidOn.ifBlank { "2026-01-01" }.take(20),
        "voided" to voided,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class CloudAttendanceSummary(
    @DocumentId val id: String = "",
    val userId: String = "",
    val batchName: String = "",
    val attDate: String = "",
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "batchName" to batchName.ifBlank { "Batch" }.take(120),
        "attDate" to attDate.ifBlank { "2026-01-01" }.take(20),
        "presentCount" to presentCount.coerceAtLeast(0),
        "absentCount" to absentCount.coerceAtLeast(0),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "batchName" to batchName.ifBlank { "Batch" }.take(120),
        "attDate" to attDate.ifBlank { "2026-01-01" }.take(20),
        "presentCount" to presentCount.coerceAtLeast(0),
        "absentCount" to absentCount.coerceAtLeast(0),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class CloudActivityEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val actorName: String = "",
    val action: String = "",
    val details: String = "",
    val recordedAt: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "actorName" to actorName.ifBlank { "System" }.take(120),
        "action" to action.ifBlank { "event" }.take(60),
        "details" to details.take(500),
        "recordedAt" to recordedAt.ifBlank { "2026-01-01 00:00" }.take(30),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "actorName" to actorName.ifBlank { "System" }.take(120),
        "action" to action.ifBlank { "event" }.take(60),
        "details" to details.take(500),
        "recordedAt" to recordedAt.ifBlank { "2026-01-01 00:00" }.take(30),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
