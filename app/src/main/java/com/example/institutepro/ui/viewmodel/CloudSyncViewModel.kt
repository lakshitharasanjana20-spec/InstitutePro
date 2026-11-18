package com.example.institutepro.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.institutepro.data.cloud.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

class CloudSyncViewModel(
    private val repo: CloudSyncRepository,
    private val currentUserId: String
) : ViewModel() {

    private companion object {
        const val TAG = "CloudSyncVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }

    val profileState: StateFlow<CloudUiState<UserProfile?>> = repo.observeProfile(currentUserId)
        .map<UserProfile?, CloudUiState<UserProfile?>> { CloudUiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing profile", root)
            emit(CloudUiState.Error(root.message ?: "Failed to load cloud profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CloudUiState.Loading)

    val studentsState: StateFlow<CloudUiState<List<CloudStudent>>> = repo.observeCloudStudents(currentUserId)
        .map<List<CloudStudent>, CloudUiState<List<CloudStudent>>> { CloudUiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing cloud students", root)
            emit(CloudUiState.Error(root.message ?: "Failed to load cloud students"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CloudUiState.Loading)

    val paymentsState: StateFlow<CloudUiState<List<CloudPayment>>> = repo.observeCloudPayments(currentUserId)
        .map<List<CloudPayment>, CloudUiState<List<CloudPayment>>> { CloudUiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing cloud payments", root)
            emit(CloudUiState.Error(root.message ?: "Failed to load cloud payments"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CloudUiState.Loading)

    val attendanceState: StateFlow<CloudUiState<List<CloudAttendanceSummary>>> = repo.observeCloudAttendance(currentUserId)
        .map<List<CloudAttendanceSummary>, CloudUiState<List<CloudAttendanceSummary>>> { CloudUiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing cloud attendance", root)
            emit(CloudUiState.Error(root.message ?: "Failed to load cloud attendance"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CloudUiState.Loading)

    val activityState: StateFlow<CloudUiState<List<CloudActivityEntry>>> = repo.observeCloudActivity(currentUserId)
        .map<List<CloudActivityEntry>, CloudUiState<List<CloudActivityEntry>>> { CloudUiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing cloud activity", root)
            emit(CloudUiState.Error(root.message ?: "Failed to load cloud activity"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CloudUiState.Loading)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncFeedback = MutableStateFlow<String?>(null)
    val syncFeedback: StateFlow<String?> = _syncFeedback.asStateFlow()

    fun clearFeedback() {
        _syncFeedback.value = null
    }

    fun ensureSignedInProfile(
        displayName: String,
        email: String?,
        photoUrl: String?,
        activeRole: String
    ) {
        viewModelScope.launch {
            repo.ensureUserProfile(displayName, email, photoUrl, activeRole)
        }
    }

    fun syncActiveRole(role: String) {
        viewModelScope.launch {
            repo.updateActiveRole(role)
        }
    }

    fun syncWorkspaceSnapshot(snapshot: AppDataSnapshot) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val studentItems = snapshot.students.map { s ->
                    val branchName = snapshot.branches.firstOrNull { it.id == s.branchId }?.name ?: "Main"
                    "stu_${s.id}" to CloudStudent(
                        userId = currentUserId,
                        studentCode = s.studentCode,
                        fullName = s.fullName,
                        gender = if (s.gender.equals("Female", true)) "Female" else "Male",
                        branchName = branchName,
                        status = if (s.status == "inactive") "inactive" else "active",
                        phone = s.phone,
                        registeredAt = s.registeredAt
                    )
                }

                val paymentItems = snapshot.payments.take(25).map { p ->
                    val st = snapshot.students.firstOrNull { it.id == p.studentId }
                    "pay_${p.id}" to CloudPayment(
                        userId = currentUserId,
                        receiptNo = p.receiptNo,
                        studentName = st?.fullName ?: "Student #${p.studentId}",
                        studentCode = st?.studentCode ?: "STU",
                        amount = p.amount,
                        method = p.method,
                        paidOn = p.paidOn,
                        voided = p.voidedAt != null
                    )
                }

                val attGroups = snapshot.attendance.groupBy { it.batchId to it.attDate }
                val attItems = attGroups.entries.take(20).map { (key, recs) ->
                    val (batchId, attDate) = key
                    val batchName = snapshot.batches.firstOrNull { it.id == batchId }?.name ?: "Batch #$batchId"
                    val present = recs.count { it.status == "present" }
                    val absent = recs.count { it.status == "absent" }
                    "att_${batchId}_${attDate}" to CloudAttendanceSummary(
                        userId = currentUserId,
                        batchName = batchName,
                        attDate = attDate,
                        presentCount = present,
                        absentCount = absent
                    )
                }

                val logItems = snapshot.activityLogs.take(15).map { log ->
                    "log_${log.id}" to CloudActivityEntry(
                        userId = currentUserId,
                        actorName = log.userName ?: "System",
                        action = log.action,
                        details = log.details,
                        recordedAt = log.createdAt
                    )
                }

                val result = repo.syncWorkspaceBatch(
                    students = studentItems,
                    payments = paymentItems,
                    attendance = attItems,
                    activity = logItems
                )
                result.onSuccess { count ->
                    _syncFeedback.value = "Synced $count records to Cloud Firestore."
                }.onFailure { err ->
                    _syncFeedback.value = err.message ?: "Cloud sync failed."
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
