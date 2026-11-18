package com.example.institutepro

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.institutepro.data.cloud.CloudSyncRepository
import com.example.institutepro.data.cloud.authStateFlow
import com.example.institutepro.data.cloud.signOutGoogle
import com.example.institutepro.data.db.UserEntity
import com.example.institutepro.data.repo.AttendanceAlerts
import com.example.institutepro.data.repo.DomainRules
import com.example.institutepro.ui.screens.*
import com.example.institutepro.ui.theme.CobaltPrimary
import com.example.institutepro.ui.theme.CrimsonAlert
import com.example.institutepro.ui.theme.EmeraldSuccess
import com.example.institutepro.ui.theme.InstituteProTheme
import com.example.institutepro.ui.viewmodel.AppDataSnapshot
import com.example.institutepro.ui.viewmodel.CloudSyncViewModel
import com.example.institutepro.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        setContent {
            InstituteProTheme {
                InstituteProRoot()
            }
        }
    }
}

private data class NavMeta(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val subtitle: String = ""
)

private fun metaForMenuKey(key: String): NavMeta = when (key) {
    "dashboard" -> NavMeta("dashboard", "Dashboard", Icons.Filled.Dashboard, "Overview & charts")
    "students" -> NavMeta("students", "Students", Icons.Filled.School, "Directory & registration")
    "courses" -> NavMeta("courses", "Courses", Icons.AutoMirrored.Filled.MenuBook, "Batches & catalog")
    "branches" -> NavMeta("branches", "Branches", Icons.Filled.AccountTree, "Campus locations")
    "schedule" -> NavMeta("schedule", "Schedule", Icons.Filled.CalendarMonth, "Timetable & covers")
    "attendance" -> NavMeta("attendance", "Attendance", Icons.Filled.FactCheck, "Roll call & punches")
    "exams" -> NavMeta("exams", "Exams", Icons.AutoMirrored.Filled.Assignment, "Tests & grading")
    "materials" -> NavMeta("materials", "Learning Hub", Icons.Filled.PlayLesson, "Notes, tasks & quizzes")
    "staff" -> NavMeta("staff", "Staff", Icons.Filled.Badge, "Team & permissions")
    "teachers" -> NavMeta("teachers", "Teachers", Icons.Filled.CoPresent, "Faculty & classes")
    "leaves" -> NavMeta("leaves", "Leaves", Icons.AutoMirrored.Filled.EventNote, "Apply & approve")
    "payments" -> NavMeta("payments", "Payments", Icons.Filled.Payments, "Fee collection & receipts")
    "salaries" -> NavMeta("salaries", "Salaries", Icons.Filled.AccountBalanceWallet, "Payroll & expenses")
    "reports" -> NavMeta("reports", "Reports", Icons.Filled.Assessment, "Charts, dues & trends")
    "settings" -> NavMeta("settings", "Settings", Icons.Filled.Settings, "Institute & roles")
    "activity" -> NavMeta("activity", "Activity Log", Icons.Filled.History, "Recent audit trail")
    "profile" -> NavMeta("profile", "Profile", Icons.Filled.Person, "Account & password")
    "sdash" -> NavMeta("sdash", "Home", Icons.Filled.Home, "Student overview")
    "sclasses" -> NavMeta("sclasses", "Classes", Icons.Filled.Class, "Enrolled batches")
    "sschedule" -> NavMeta("sschedule", "Timetable", Icons.Filled.CalendarMonth, "Weekly schedule")
    "sassign" -> NavMeta("sassign", "Assignments", Icons.AutoMirrored.Filled.Assignment, "Homework tasks")
    "squiz" -> NavMeta("squiz", "Quizzes", Icons.Filled.Quiz, "Practice assessments")
    "sresults" -> NavMeta("sresults", "Results", Icons.Filled.Grade, "Exam marks & grades")
    "sattendance" -> NavMeta("sattendance", "Attendance", Icons.Filled.FactCheck, "Attendance record")
    "sfees" -> NavMeta("sfees", "Fees", Icons.Filled.ReceiptLong, "Invoices & receipts")
    "sprofile" -> NavMeta("sprofile", "Profile", Icons.Filled.Person, "Account & password")
    "cloud_sync" -> NavMeta("cloud_sync", "Cloud Sync", Icons.Filled.CloudSync, "Firestore backup & sync")
    else -> NavMeta(key, key.replaceFirstChar { it.uppercase() }, Icons.Filled.Apps, "")
}

@Composable
fun InstituteProRoot(vm: MainViewModel = viewModel()) {
    val data by vm.dataState.collectAsStateWithLifecycle()
    val currentUser by vm.currentUser.collectAsStateWithLifecycle()
    val auth = remember { Firebase.auth }
    val firebaseUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    if (!data.initialized) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Preparing InstitutePro workspace...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        return
    }

    val fbUser = firebaseUser
    val user = currentUser

    if (user == null) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = rememberCoroutineScope()
        val credentialManager = remember(context) { CredentialManager.create(context) }

        LoginScreen(
            vm = vm,
            instituteName = data.instituteName,
            googleUserEmail = fbUser?.email ?: fbUser?.displayName,
            onGoogleAuthSuccess = {
                if (vm.currentUser.value == null) {
                    vm.quickDemoSignIn("super_admin")
                }
            },
            onSignOutGoogle = if (fbUser != null) {
                { signOutGoogle(credentialManager, scope) }
            } else null
        )
    } else if (user.mustChangePw) {
        ForceChangePasswordScreen(vm = vm)
    } else if (fbUser == null) {
        AuthenticatedShell(
            vm = vm,
            user = user,
            data = data,
            firebaseUser = null,
            cloudVm = null,
            onSignOutGoogle = { vm.signOut() }
        )
    } else {
        key(fbUser.uid) {
            val cloudVm: CloudSyncViewModel = viewModel(
                key = fbUser.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val firestore = FirebaseFirestore.getInstance(databaseId)
                        CloudSyncViewModel(
                            repo = CloudSyncRepository(firestore),
                            currentUserId = fbUser.uid
                        )
                    }
                }
            )
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = rememberCoroutineScope()
            val credentialManager = remember(context) { CredentialManager.create(context) }

            LaunchedEffect(fbUser.uid) {
                cloudVm.ensureSignedInProfile(
                    displayName = fbUser.displayName ?: "InstitutePro User",
                    email = fbUser.email,
                    photoUrl = fbUser.photoUrl?.toString(),
                    activeRole = user.role
                )
            }
            LaunchedEffect(user.role) {
                cloudVm.syncActiveRole(user.role)
            }

            AuthenticatedShell(
                vm = vm,
                user = user,
                data = data,
                firebaseUser = fbUser,
                cloudVm = cloudVm,
                onSignOutGoogle = {
                    signOutGoogle(credentialManager, scope)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShell(
    vm: MainViewModel,
    user: UserEntity,
    data: AppDataSnapshot,
    firebaseUser: FirebaseUser?,
    cloudVm: CloudSyncViewModel?,
    onSignOutGoogle: () -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val toastMsg by vm.toastMessage.collectAsStateWithLifecycle()
    val alerts by vm.attendanceAlerts.collectAsStateWithLifecycle()
    val branchFilter by vm.branchFilter.collectAsStateWithLifecycle()

    LaunchedEffect(toastMsg) {
        val msg = toastMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.clearToast()
        }
    }

    val isStudent = DomainRules.isStudent(user.role)
    val allowedMenus = remember(user.role, data.roleMenus) {
        val configured = data.roleMenus.filter { it.role == user.role }.map { it.menuKey }
        val base = if (configured.isNotEmpty()) configured
        else if (isStudent) listOf("sdash", "sclasses", "sassign", "squiz", "sresults", "sattendance", "sfees", "sprofile")
        else listOf("dashboard", "profile")
        if ("cloud_sync" in base) base else base + "cloud_sync"
    }

    val startDest = remember(user.role, allowedMenus) {
        if (isStudent) {
            if ("sdash" in allowedMenus) "sdash" else allowedMenus.first()
        } else {
            if ("dashboard" in allowedMenus) "dashboard" else allowedMenus.first()
        }
    }

    val preferredPrimary = remember(isStudent, user.role) {
        if (isStudent) listOf("sdash", "sclasses", "sassign", "squiz")
        else when (user.role) {
            "finance" -> listOf("dashboard", "payments", "salaries", "reports")
            "teacher" -> listOf("dashboard", "courses", "materials", "exams")
            "other" -> listOf("dashboard", "leaves", "profile")
            else -> listOf("dashboard", "students", "attendance", "payments")
        }
    }

    val primaryMenus = remember(allowedMenus, preferredPrimary) {
        val chosen = preferredPrimary.filter { it in allowedMenus }.toMutableList()
        if (chosen.size < 4) {
            allowedMenus.filter { it !in chosen }.take(4 - chosen.size).forEach { chosen.add(it) }
        }
        chosen.map(::metaForMenuKey)
    }

    val overflowMenus = remember(allowedMenus, primaryMenus) {
        val primaryKeys = primaryMenus.map { it.key }.toSet()
        allowedMenus.filter { it !in primaryKeys }.map(::metaForMenuKey)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startDest
    val isDetailRoute = currentRoute.contains("/{")

    var showMoreSheet by remember { mutableStateOf(false) }
    var showBranchMenu by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    fun navigateTopLevel(route: String) {
        if (route !in allowedMenus) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val currentTitle = remember(currentRoute, data.instituteName) {
        when {
            currentRoute.startsWith("student_detail/") -> "Student Profile"
            currentRoute.startsWith("batch_detail/") -> "Batch Overview"
            currentRoute.startsWith("exam_marks/") -> "Exam Marks"
            currentRoute.startsWith("learning_hub/") -> "Learning Hub"
            currentRoute.startsWith("student_class/") -> "Class Hub"
            currentRoute.startsWith("quiz_runner/") -> "Quiz Assessment"
            else -> metaForMenuKey(currentRoute).label
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail && !isDetailRoute) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(Modifier.height(12.dp))
                    primaryMenus.forEach { item ->
                        NavigationRailItem(
                            selected = currentRoute == item.key,
                            onClick = { navigateTopLevel(item.key) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.testTag("nav_${item.key}")
                        )
                    }
                    if (overflowMenus.isNotEmpty()) {
                        NavigationRailItem(
                            selected = currentRoute in overflowMenus.map { it.key },
                            onClick = { showMoreSheet = true },
                            icon = { Icon(Icons.Filled.GridView, contentDescription = "More") },
                            label = { Text("More") },
                            modifier = Modifier.testTag("nav_more")
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            if (isDetailRoute) {
                                IconButton(
                                    onClick = { navController.popBackStack() },
                                    modifier = Modifier.testTag("top_back_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = currentTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${user.fullName} · ${DomainRules.ROLE_LABELS[user.role] ?: user.role}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            if (DomainRules.isOwner(user.role) && !isDetailRoute) {
                                Box {
                                    val selectedBranchName = data.branches.firstOrNull { it.id == branchFilter }?.code ?: "ALL"
                                    TextButton(
                                        onClick = { showBranchMenu = true },
                                        modifier = Modifier.testTag("branch_filter_button")
                                    ) {
                                        Icon(Icons.Filled.LocationCity, contentDescription = "Branch filter", modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(selectedBranchName)
                                    }
                                    DropdownMenu(
                                        expanded = showBranchMenu,
                                        onDismissRequest = { showBranchMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("All Branches") },
                                            onClick = {
                                                vm.setBranchFilter(null)
                                                showBranchMenu = false
                                            }
                                        )
                                        data.branches.forEach { br ->
                                            DropdownMenuItem(
                                                text = { Text("${br.code} — ${br.name}") },
                                                onClick = {
                                                    vm.setBranchFilter(br.id)
                                                    showBranchMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            if (!isDetailRoute) {
                                IconButton(
                                    onClick = { navigateTopLevel("cloud_sync") },
                                    modifier = Modifier.testTag("top_cloud_sync_button")
                                ) {
                                    Icon(
                                        imageVector = if (firebaseUser != null) Icons.Filled.CloudDone else Icons.Filled.CloudQueue,
                                        contentDescription = "Cloud Sync",
                                        tint = if (firebaseUser != null) CobaltPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showSignOutConfirm = true },
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!useRail && !isDetailRoute) {
                        NavigationBar {
                            primaryMenus.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.key,
                                    onClick = { navigateTopLevel(item.key) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.testTag("nav_${item.key}")
                                )
                            }
                            if (overflowMenus.isNotEmpty()) {
                                NavigationBarItem(
                                    selected = currentRoute in overflowMenus.map { it.key },
                                    onClick = { showMoreSheet = true },
                                    icon = { Icon(Icons.Filled.GridView, contentDescription = "More modules") },
                                    label = { Text("More") },
                                    modifier = Modifier.testTag("nav_more")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = startDest,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Staff & Admin routes
                    composable("dashboard") {
                        DashboardScreen(
                            vm = vm,
                            user = user,
                            data = data,
                            allowedMenus = allowedMenus,
                            onNavigate = { navigateTopLevel(it) },
                            onOpenBatch = { navController.navigate("batch_detail/$it") },
                            onOpenStudent = { navController.navigate("student_detail/$it") },
                            onOpenExamMarks = { navController.navigate("exam_marks/$it") }
                        )
                    }
                    composable("students") {
                        StudentsScreen(
                            vm = vm,
                            user = user,
                            data = data,
                            onOpenStudent = { navController.navigate("student_detail/$it") }
                        )
                    }
                    composable("courses") {
                        CoursesScreen(
                            vm = vm,
                            user = user,
                            data = data,
                            onOpenBatch = { navController.navigate("batch_detail/$it") }
                        )
                    }
                    composable("branches") {
                        BranchesScreen(vm = vm, data = data)
                    }
                    composable("schedule") {
                        ScheduleScreen(
                            vm = vm,
                            user = user,
                            data = data,
                            onOpenBatch = { navController.navigate("batch_detail/$it") },
                            onOpenExamMarks = { navController.navigate("exam_marks/$it") }
                        )
                    }
                    composable("attendance") {
                        AttendanceScreen(vm = vm, user = user, data = data)
                    }
                    composable("exams") {
                        ExamsScreen(
                            vm = vm,
                            user = user,
                            data = data,
                            onOpenExamMarks = { navController.navigate("exam_marks/$it") }
                        )
                    }
                    composable("materials") {
                        LearningHubScreen(
                            vm = vm,
                            user = user,
                            data = data,
                            initialBatchId = null,
                            onBack = null
                        )
                    }
                    composable("staff") {
                        StaffDirectoryScreen(vm = vm, user = user, data = data, teachersOnly = false)
                    }
                    composable("teachers") {
                        StaffDirectoryScreen(vm = vm, user = user, data = data, teachersOnly = true)
                    }
                    composable("leaves") {
                        LeavesScreen(vm = vm, user = user, data = data)
                    }
                    composable("payments") {
                        PaymentsScreen(vm = vm, user = user, data = data)
                    }
                    composable("salaries") {
                        SalariesScreen(vm = vm, user = user, data = data)
                    }
                    composable("reports") {
                        ReportsScreen(
                            vm = vm,
                            user = user,
                            data = data,
                            onOpenStudent = { navController.navigate("student_detail/$it") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(vm = vm, user = user, data = data)
                    }
                    composable("activity") {
                        ActivityLogScreen(data = data)
                    }
                    composable("profile") {
                        ProfileScreen(vm = vm, user = user, data = data)
                    }
                    composable("cloud_sync") {
                        if (firebaseUser != null && cloudVm != null) {
                            CloudSyncScreen(
                                firebaseUser = firebaseUser,
                                cloudVm = cloudVm,
                                snapshot = data,
                                onSignOutGoogle = onSignOutGoogle
                            )
                        } else {
                            ConnectGoogleCloudScreen()
                        }
                    }

                    // Student Portal routes
                    composable("sdash") {
                        StudentHomeScreen(
                            user = user,
                            data = data,
                            onNavigate = { navigateTopLevel(it) },
                            onOpenClass = { navController.navigate("student_class/$it") },
                            onTakeQuiz = { navController.navigate("quiz_runner/$it") }
                        )
                    }
                    composable("sclasses") {
                        StudentClassesScreen(
                            user = user,
                            data = data,
                            onOpenClass = { navController.navigate("student_class/$it") }
                        )
                    }
                    composable("sschedule") {
                        ScheduleScreen(
                            vm = vm,
                            user = user,
                            data = data,
                            onOpenBatch = { navController.navigate("student_class/$it") },
                            onOpenExamMarks = { navigateTopLevel("sresults") }
                        )
                    }
                    composable("sassign") {
                        StudentAssignmentsScreen(vm = vm, user = user, data = data)
                    }
                    composable("squiz") {
                        StudentQuizzesScreen(
                            user = user,
                            data = data,
                            onTakeQuiz = { navController.navigate("quiz_runner/$it") }
                        )
                    }
                    composable("sresults") {
                        StudentResultsAttendanceFeesScreen(mode = "sresults", user = user, data = data)
                    }
                    composable("sattendance") {
                        StudentResultsAttendanceFeesScreen(mode = "sattendance", user = user, data = data)
                    }
                    composable("sfees") {
                        StudentResultsAttendanceFeesScreen(mode = "sfees", user = user, data = data)
                    }
                    composable("sprofile") {
                        ProfileScreen(vm = vm, user = user, data = data)
                    }

                    // Detail routes
                    composable(
                        route = "student_detail/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val id = backStackEntry.arguments?.getInt("id") ?: 0
                        StudentDetailScreen(
                            vm = vm,
                            user = user,
                            data = data,
                            studentId = id,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "batch_detail/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val id = backStackEntry.arguments?.getInt("id") ?: 0
                        BatchDetailScreen(
                            data = data,
                            batchId = id,
                            onBack = { navController.popBackStack() },
                            onOpenStudent = { navController.navigate("student_detail/$it") },
                            onOpenLearningHub = { navController.navigate("learning_hub/$it") }
                        )
                    }
                    composable(
                        route = "exam_marks/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val id = backStackEntry.arguments?.getInt("id") ?: 0
                        ExamMarksScreen(
                            vm = vm,
                            user = user,
                            data = data,
                            examId = id,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "learning_hub/{batchId}",
                        arguments = listOf(navArgument("batchId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val batchId = backStackEntry.arguments?.getInt("batchId")
                        LearningHubScreen(
                            vm = vm,
                            user = user,
                            data = data,
                            initialBatchId = batchId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "student_class/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val id = backStackEntry.arguments?.getInt("id") ?: 0
                        StudentClassDetailScreen(
                            data = data,
                            batchId = id,
                            onBack = { navController.popBackStack() },
                            onTakeQuiz = { navController.navigate("quiz_runner/$it") }
                        )
                    }
                    composable(
                        route = "quiz_runner/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val id = backStackEntry.arguments?.getInt("id") ?: 0
                        InteractiveQuizRunnerScreen(
                            vm = vm,
                            user = user,
                            data = data,
                            materialId = id,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    if (showMoreSheet) {
        var sheetQuery by remember { mutableStateOf("") }
        val filteredOverflow = remember(overflowMenus, sheetQuery) {
            if (sheetQuery.isBlank()) overflowMenus
            else overflowMenus.filter {
                it.label.contains(sheetQuery, true) || it.subtitle.contains(sheetQuery, true)
            }
        }
        ModalBottomSheet(onDismissRequest = { showMoreSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("All Workspace Modules", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Choose any module below or switch quick tools",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (overflowMenus.size > 4) {
                    Spacer(Modifier.height(10.dp))
                    com.example.institutepro.ui.components.SearchField(
                        value = sheetQuery,
                        onValueChange = { sheetQuery = it },
                        placeholder = "Find a module...",
                        testTag = "more_module_search"
                    )
                }
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 136.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(filteredOverflow, key = { it.key }) { item ->
                        val selected = currentRoute == item.key
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showMoreSheet = false
                                    navigateTopLevel(item.key)
                                }
                                .testTag("more_${item.key}")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (selected) CobaltPrimary else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.subtitle.isNotBlank()) {
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = CobaltPrimary) },
            title = { Text("Sign Out?") },
            text = {
                Text(
                    if (firebaseUser != null)
                        "Sign out of your InstitutePro role (${user.fullName}), or also disconnect your Google account (${firebaseUser.email ?: "connected"})."
                    else
                        "You are signed in as ${user.fullName}. Do you want to sign out and return to the login screen?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showSignOutConfirm = false
                    vm.signOut()
                }) { Text("Sign Out") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (firebaseUser != null) {
                        TextButton(onClick = {
                            showSignOutConfirm = false
                            vm.signOut()
                            onSignOutGoogle()
                        }) { Text("Sign Out Google") }
                    }
                    TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
                }
            }
        )
    }

    alerts?.let { alert ->
        AttendanceAlertsDialog(alert = alert, onDismiss = { vm.clearAttendanceAlerts() })
    }
}

@Composable
private fun AttendanceAlertsDialog(alert: AttendanceAlerts, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attendance Status Updates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (alert.markedInactive.isNotEmpty()) {
                    Text(
                        "Auto-marked Inactive (2 consecutive absences):",
                        style = MaterialTheme.typography.labelLarge,
                        color = CrimsonAlert,
                        fontWeight = FontWeight.Bold
                    )
                    alert.markedInactive.forEach { (sid, name) ->
                        Text("• $name ($sid)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (alert.reactivated.isNotEmpty()) {
                    Text(
                        "Auto-reactivated on Present:",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldSuccess,
                        fontWeight = FontWeight.Bold
                    )
                    alert.reactivated.forEach { (sid, name) ->
                        Text("• $name ($sid)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Got it") }
        }
    )
}
