# InstitutePro (Android)

Native **Kotlin + Jetpack Compose + Material 3 + Room** rewrite of `lakshitharasanjana20-spec/InstitutePro`, a multi-branch Institute Management System and Student Learning Hub.

## Core Features Preserved

- **Role-Based Authentication & Access Matrix**: 9 roles (`super_admin`, `admin`, `branch_manager`, `administrator`, `receptionist`, `teacher`, `finance`, `other`, `student`) with customizable per-role module permissions, account lock/inactive checks, forced password changes, and quick demo sign-in buttons.
- **Multi-Branch & Course Management**: Branch directory (`MN`, `KD`, etc.), owner branch filtering, course catalog (`Annual Intake`, `Grade Classes`, `Short Courses`), batch creation with timetable clash detection, and room/teacher scheduling.
- **Student Admissions & Profiles**: Automatic structured student ID generation (`BRANCH-TYPE-COURSE-YEAR-SEQ`), age-aware validation (18+ NIC format check vs. <18 guardian details), optional portal account provisioning, and full student profiles (attendance, exams, fees, payments).
- **Attendance & Biometric Punch Rules**: Batch attendance marking, 7-day institute attendance visualization, staff attendance, CSV/TXT fingerprint punch log parser, automatic inactive transition after 2 consecutive absences, and automatic reactivation on present.
- **Exams, Marks & Learning Hub (LMS)**: Exam scheduling, batch marks entry with configurable grade bands (`A/B/C/S/W`) and final outcomes (`Course completed` / `Repeat required` / `Eligible for next grade`), plus Learning Hub materials (recorded lectures, live classes, course materials, assignments, MCQ quizzes, extra readings, announcements).
- **Fees, Payroll & Reports**: Flexible installment fee accounts, scholarships, oldest-due payment allocation, official receipts, payment voiding with audit logs, staff salary structures, monthly payroll generation with EPF/ETF & payslips, branch expenses, leave requests, substitute teacher assignments, and financial summary reports.
- **Student Portal**: Dedicated student dashboard, enrolled classes, timetable, homework submission, interactive timed MCQ quiz runner with auto-grading, exam results, attendance log, and fee installment breakdown.

## Demo Accounts

All seeded accounts share the password **`Institute@123`** (or use the Quick Demo Access chips on the login screen):

| Role | Email |
| :--- | :--- |
| Super Admin | `admin@institutepro.lk` |
| Branch Manager | `manager@institutepro.lk` |
| Teacher | `dilani@institutepro.lk` |
| Finance Officer | `finance@institutepro.lk` |
| Student Portal | `student@institutepro.lk` |

## Architecture Notes

- **Local Persistence**: Replaces the original PHP/MySQL backend with an on-device **Room (SQLite)** database (`27` relational entities) pre-populated on first launch via `SeedData`.
- **External Resources & Imports**: File uploads/downloads from the web version are adapted to external URL links/intents for live classes and resources, and an in-app CSV/TXT text paste importer for biometric fingerprint punch logs.
