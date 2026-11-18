package com.example.institutepro.data.cloud

import android.content.Context
import com.example.institutepro.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Cloud Firestore.")

    private fun sanitizeDocId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        return cleaned.ifBlank { "doc_item" }
    }

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val logEx = fsError ?: (error as? Exception)
                    if (logEx != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(logEx, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudStudents(userId: String): Flow<List<CloudStudent>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "users/$uid/students"
        emitAll(
            db.collection("users").document(uid).collection("students")
                .snapshots()
                .map { snap -> snap.toObjects(CloudStudent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val logEx = fsError ?: (error as? Exception)
                    if (logEx != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(logEx, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudPayments(userId: String): Flow<List<CloudPayment>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "users/$uid/payments"
        emitAll(
            db.collection("users").document(uid).collection("payments")
                .snapshots()
                .map { snap -> snap.toObjects(CloudPayment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val logEx = fsError ?: (error as? Exception)
                    if (logEx != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(logEx, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudAttendance(userId: String): Flow<List<CloudAttendanceSummary>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "users/$uid/attendance"
        emitAll(
            db.collection("users").document(uid).collection("attendance")
                .snapshots()
                .map { snap -> snap.toObjects(CloudAttendanceSummary::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val logEx = fsError ?: (error as? Exception)
                    if (logEx != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(logEx, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudActivity(userId: String): Flow<List<CloudActivityEntry>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "users/$uid/activity"
        emitAll(
            db.collection("users").document(uid).collection("activity")
                .snapshots()
                .map { snap -> snap.toObjects(CloudActivityEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsError = unwrapFirestoreError(error)
                    if (fsError == null && error is CancellationException) throw error
                    val logEx = fsError ?: (error as? Exception)
                    if (logEx != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(logEx, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String?,
        photoUrl: String?,
        activeRole: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            val snap = ref.get().await()
            if (snap.exists()) {
                val updatePayload = UserProfile.toUpdateMap(
                    displayName = displayName,
                    email = email,
                    photoUrl = photoUrl,
                    activeRole = activeRole
                )
                ref.update(updatePayload).await()
            } else {
                val createPayload = UserProfile.toCreateMap(
                    uid = uid,
                    displayName = displayName,
                    email = email,
                    photoUrl = photoUrl,
                    activeRole = activeRole
                )
                ref.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateActiveRole(role: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid)
            docPath = ref.path
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(
                    mapOf(
                        "activeRole" to UserProfile.sanitizeRole(role),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val fbUser = auth.currentUser
                ref.set(
                    UserProfile.toCreateMap(
                        uid = uid,
                        displayName = fbUser?.displayName ?: "InstitutePro User",
                        email = fbUser?.email,
                        photoUrl = fbUser?.photoUrl?.toString(),
                        activeRole = role
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertStudent(docIdRaw: String, student: CloudStudent): Result<Unit> {
        val docId = sanitizeDocId(docIdRaw)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/students/$docId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("students").document(docId)
            docPath = ref.path
            if (ref.get().await().exists()) {
                ref.update(student.toUpdateMap()).await()
            } else {
                ref.set(student.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudStudent(docIdRaw: String): Result<Unit> {
        val docId = sanitizeDocId(docIdRaw)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/students/$docId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("students").document(docId)
            docPath = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertPayment(docIdRaw: String, payment: CloudPayment): Result<Unit> {
        val docId = sanitizeDocId(docIdRaw)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/payments/$docId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("payments").document(docId)
            docPath = ref.path
            if (ref.get().await().exists()) {
                ref.update(payment.toUpdateMap()).await()
            } else {
                ref.set(payment.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertAttendanceSummary(docIdRaw: String, summary: CloudAttendanceSummary): Result<Unit> {
        val docId = sanitizeDocId(docIdRaw)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/attendance/$docId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("attendance").document(docId)
            docPath = ref.path
            if (ref.get().await().exists()) {
                ref.update(summary.toUpdateMap()).await()
            } else {
                ref.set(summary.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertActivity(docIdRaw: String, entry: CloudActivityEntry): Result<Unit> {
        val docId = sanitizeDocId(docIdRaw)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/activity/$docId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("activity").document(docId)
            docPath = ref.path
            if (ref.get().await().exists()) {
                ref.update(entry.toUpdateMap()).await()
            } else {
                ref.set(entry.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncWorkspaceBatch(
        students: List<Pair<String, CloudStudent>>,
        payments: List<Pair<String, CloudPayment>>,
        attendance: List<Pair<String, CloudAttendanceSummary>>,
        activity: List<Pair<String, CloudActivityEntry>>
    ): Result<Int> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            var count = 0
            for ((rawId, s) in students) {
                upsertStudent(rawId, s).getOrThrow()
                count++
            }
            for ((rawId, p) in payments) {
                upsertPayment(rawId, p).getOrThrow()
                count++
            }
            for ((rawId, a) in attendance) {
                upsertAttendanceSummary(rawId, a).getOrThrow()
                count++
            }
            for ((rawId, l) in activity) {
                upsertActivity(rawId, l).getOrThrow()
                count++
            }
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }
}
