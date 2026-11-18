SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS branches (
  id INT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(10) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  address VARCHAR(255), phone VARCHAR(30),
  status ENUM('active','inactive') DEFAULT 'active',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  branch_id INT NULL,
  role ENUM('super_admin','admin','branch_manager','administrator','receptionist','teacher','finance','other') NOT NULL,
  full_name VARCHAR(150) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  phone VARCHAR(30), nic VARCHAR(20), address VARCHAR(255),
  date_of_birth DATE NULL, job_title VARCHAR(100), joined_on DATE NULL,
  status ENUM('active','inactive') DEFAULT 'active',
  failed_logins INT DEFAULT 0, locked_until DATETIME NULL, last_login DATETIME NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE SET NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS role_menu (
  role VARCHAR(30) NOT NULL, menu_key VARCHAR(30) NOT NULL,
  PRIMARY KEY (role, menu_key)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS activity_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NULL, branch_id INT NULL,
  action VARCHAR(60) NOT NULL, details VARCHAR(500),
  ip_address VARCHAR(45), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX (user_id), INDEX (created_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS settings (
  k VARCHAR(60) PRIMARY KEY, v TEXT
) ENGINE=InnoDB;
INSERT IGNORE INTO settings (k,v) VALUES ('institute_name','InstitutePro English Institute'),('session_minutes','30');
-- Menu matrix (Super Admin can edit this later from Settings)
INSERT IGNORE INTO role_menu (role,menu_key) VALUES
('super_admin','dashboard'),('super_admin','students'),('super_admin','courses'),('super_admin','branches'),('super_admin','schedule'),('super_admin','attendance'),('super_admin','exams'),('super_admin','staff'),('super_admin','teachers'),('super_admin','payments'),('super_admin','salaries'),('super_admin','reports'),('super_admin','settings'),('super_admin','activity'),('super_admin','profile'),
('admin','dashboard'),('admin','students'),('admin','courses'),('admin','branches'),('admin','schedule'),('admin','attendance'),('admin','exams'),('admin','staff'),('admin','teachers'),('admin','payments'),('admin','salaries'),('admin','reports'),('admin','settings'),('admin','activity'),('admin','profile'),
('branch_manager','dashboard'),('branch_manager','students'),('branch_manager','courses'),('branch_manager','schedule'),('branch_manager','attendance'),('branch_manager','exams'),('branch_manager','staff'),('branch_manager','teachers'),('branch_manager','payments'),('branch_manager','salaries'),('branch_manager','reports'),('branch_manager','settings'),('branch_manager','activity'),('branch_manager','profile'),
('administrator','dashboard'),('administrator','students'),('administrator','courses'),('administrator','schedule'),('administrator','attendance'),('administrator','exams'),('administrator','staff'),('administrator','teachers'),('administrator','payments'),('administrator','settings'),('administrator','profile'),
('receptionist','dashboard'),('receptionist','students'),('receptionist','courses'),('receptionist','schedule'),('receptionist','attendance'),('receptionist','exams'),('receptionist','staff'),('receptionist','teachers'),('receptionist','payments'),('receptionist','profile'),
('teacher','dashboard'),('teacher','students'),('teacher','courses'),('teacher','schedule'),('teacher','attendance'),('teacher','exams'),('teacher','teachers'),('teacher','staff'),('teacher','profile'),
('finance','dashboard'),('finance','payments'),('finance','salaries'),('finance','reports'),('finance','profile'),
('other','dashboard'),('other','profile');
