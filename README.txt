InstitutePro – Foundation build
1. Upload folder to PHP 8+/MySQL 8+ host (Apache).
2. Edit config.php (DB details + INSTALL_KEY).
3. Open install.php?key=YOUR_KEY, create the Super Admin, then DELETE install.php.
4. Sign in. Menu per role comes from the role_menu table.
Rule for all later modules: scope every query with branch_scope() (null = all branches).
