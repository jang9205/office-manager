-- Office Manager database schema
-- Target: MySQL 8.0.16+
-- Character set: utf8mb4
--
-- Design rules
--   1. Business timestamps are stored as DATETIME(6). The application should use one
--      consistent timezone (recommended: UTC) and convert it at the UI boundary.
--   2. Passwords, reset tokens, OTP backup codes and SMTP secrets must never be stored
--      as plain text. Store password hashes or encrypted/hashed values only.
--   3. Dashboard, statistics, integrated search, pagination and sorting are query/API
--      concerns. Supporting indexes and representative statistics views are included.
--   4. File binary data is kept outside MySQL. file_assets stores metadata and a
--      storage_key that points to local/object storage.

SET NAMES utf8mb4;

-- ============================================================================
-- 1. Company and organization
-- ============================================================================

CREATE TABLE companies (
    company_id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_code        VARCHAR(30) NOT NULL,
    company_name        VARCHAR(100) NOT NULL,
    business_number     VARCHAR(20) NULL,
    representative_name VARCHAR(50) NULL,
    phone               VARCHAR(30) NULL,
    email               VARCHAR(255) NULL,
    postal_code         VARCHAR(10) NULL,
    address             VARCHAR(255) NULL,
    address_detail      VARCHAR(255) NULL,
    logo_storage_key    VARCHAR(500) NULL,
    timezone            VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul',
    locale              VARCHAR(20) NOT NULL DEFAULT 'ko-KR',
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (company_id),
    UNIQUE KEY uk_companies_code (company_code),
    UNIQUE KEY uk_companies_business_number (business_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE departments (
    department_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    parent_department_id BIGINT UNSIGNED NULL,
    department_code     VARCHAR(30) NOT NULL,
    department_name     VARCHAR(100) NOT NULL,
    description         VARCHAR(500) NULL,
    sort_order          INT NOT NULL DEFAULT 0,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at          DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (department_id),
    UNIQUE KEY uk_departments_company_code (company_id, department_code),
    UNIQUE KEY uk_departments_company_name (company_id, department_name),
    KEY idx_departments_parent (parent_department_id),
    KEY idx_departments_active (company_id, active, deleted_at),
    CONSTRAINT fk_departments_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_departments_parent
        FOREIGN KEY (parent_department_id) REFERENCES departments (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE positions (
    position_id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    position_code       VARCHAR(30) NOT NULL,
    position_name       VARCHAR(50) NOT NULL,
    position_level      INT NOT NULL,
    sort_order          INT NOT NULL DEFAULT 0,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at          DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (position_id),
    UNIQUE KEY uk_positions_company_code (company_id, position_code),
    UNIQUE KEY uk_positions_company_name (company_id, position_name),
    KEY idx_positions_order (company_id, active, sort_order),
    CONSTRAINT fk_positions_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE employees (
    employee_id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    department_id       BIGINT UNSIGNED NULL,
    position_id         BIGINT UNSIGNED NULL,
    manager_employee_id BIGINT UNSIGNED NULL,
    employee_number     VARCHAR(30) NOT NULL,
    employee_name       VARCHAR(50) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    phone               VARCHAR(30) NULL,
    hire_date           DATE NOT NULL,
    termination_date    DATE NULL,
    employment_status   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                         COMMENT 'ACTIVE, LEAVE_OF_ABSENCE, RESIGNED',
    employment_type     VARCHAR(20) NOT NULL DEFAULT 'FULL_TIME'
                         COMMENT 'FULL_TIME, CONTRACT, PART_TIME, INTERN',
    profile_file_id     BIGINT UNSIGNED NULL COMMENT 'FK is added after file_assets',
    memo                VARCHAR(1000) NULL,
    deleted_at          DATETIME(6) NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (employee_id),
    UNIQUE KEY uk_employees_company_number (company_id, employee_number),
    UNIQUE KEY uk_employees_company_email (company_id, email),
    KEY idx_employees_name (company_id, employee_name),
    KEY idx_employees_department (department_id, employment_status, deleted_at),
    KEY idx_employees_position (position_id, employment_status, deleted_at),
    KEY idx_employees_manager (manager_employee_id),
    KEY idx_employees_hire_date (company_id, hire_date),
    KEY idx_employees_termination_date (company_id, termination_date),
    CONSTRAINT fk_employees_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_employees_department
        FOREIGN KEY (department_id) REFERENCES departments (department_id),
    CONSTRAINT fk_employees_position
        FOREIGN KEY (position_id) REFERENCES positions (position_id),
    CONSTRAINT fk_employees_manager
        FOREIGN KEY (manager_employee_id) REFERENCES employees (employee_id),
    CONSTRAINT ck_employees_termination
        CHECK (termination_date IS NULL OR termination_date >= hire_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE departments
    ADD COLUMN leader_employee_id BIGINT UNSIGNED NULL AFTER parent_department_id,
    ADD KEY idx_departments_leader (leader_employee_id),
    ADD CONSTRAINT fk_departments_leader
        FOREIGN KEY (leader_employee_id) REFERENCES employees (employee_id);

CREATE TABLE employee_department_histories (
    department_history_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT UNSIGNED NOT NULL,
    department_id       BIGINT UNSIGNED NOT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE NULL,
    change_reason       VARCHAR(500) NULL,
    changed_by_employee_id BIGINT UNSIGNED NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (department_history_id),
    KEY idx_department_histories_employee (employee_id, effective_from),
    KEY idx_department_histories_department (department_id, effective_from),
    CONSTRAINT fk_department_histories_employee
        FOREIGN KEY (employee_id) REFERENCES employees (employee_id),
    CONSTRAINT fk_department_histories_department
        FOREIGN KEY (department_id) REFERENCES departments (department_id),
    CONSTRAINT fk_department_histories_changed_by
        FOREIGN KEY (changed_by_employee_id) REFERENCES employees (employee_id),
    CONSTRAINT ck_department_histories_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE employee_position_histories (
    position_history_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT UNSIGNED NOT NULL,
    position_id         BIGINT UNSIGNED NOT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE NULL,
    change_reason       VARCHAR(500) NULL,
    changed_by_employee_id BIGINT UNSIGNED NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (position_history_id),
    KEY idx_position_histories_employee (employee_id, effective_from),
    KEY idx_position_histories_position (position_id, effective_from),
    CONSTRAINT fk_position_histories_employee
        FOREIGN KEY (employee_id) REFERENCES employees (employee_id),
    CONSTRAINT fk_position_histories_position
        FOREIGN KEY (position_id) REFERENCES positions (position_id),
    CONSTRAINT fk_position_histories_changed_by
        FOREIGN KEY (changed_by_employee_id) REFERENCES employees (employee_id),
    CONSTRAINT ck_position_histories_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- 2. Authentication, authorization and menu access
-- ============================================================================

CREATE TABLE accounts (
    account_id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT UNSIGNED NOT NULL,
    login_id            VARCHAR(100) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    account_status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                         COMMENT 'PENDING, ACTIVE, LOCKED, DISABLED',
    failed_login_count  INT UNSIGNED NOT NULL DEFAULT 0,
    locked_until        DATETIME(6) NULL,
    password_changed_at DATETIME(6) NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at       DATETIME(6) NULL,
    last_login_ip       VARCHAR(45) NULL,
    created_by_account_id BIGINT UNSIGNED NULL,
    deleted_at          DATETIME(6) NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id),
    UNIQUE KEY uk_accounts_employee (employee_id),
    UNIQUE KEY uk_accounts_login_id (login_id),
    KEY idx_accounts_status (account_status, locked_until),
    CONSTRAINT fk_accounts_employee
        FOREIGN KEY (employee_id) REFERENCES employees (employee_id),
    CONSTRAINT fk_accounts_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES accounts (account_id),
    CONSTRAINT ck_accounts_failed_count CHECK (failed_login_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE roles (
    role_id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_code           VARCHAR(50) NOT NULL COMMENT 'ROLE_ADMIN, ROLE_MANAGER, ROLE_EMPLOYEE',
    role_name           VARCHAR(50) NOT NULL,
    description         VARCHAR(255) NULL,
    system_role         BOOLEAN NOT NULL DEFAULT FALSE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_roles_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE permissions (
    permission_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    permission_code     VARCHAR(100) NOT NULL COMMENT 'EMPLOYEE:READ, LEAVE:APPROVE',
    permission_name     VARCHAR(100) NOT NULL,
    resource_name       VARCHAR(50) NOT NULL,
    action_name         VARCHAR(30) NOT NULL,
    description         VARCHAR(255) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (permission_id),
    UNIQUE KEY uk_permissions_code (permission_code),
    KEY idx_permissions_resource (resource_name, action_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_roles (
    account_id          BIGINT UNSIGNED NOT NULL,
    role_id             BIGINT UNSIGNED NOT NULL,
    assigned_by_account_id BIGINT UNSIGNED NULL,
    assigned_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at          DATETIME(6) NULL,
    PRIMARY KEY (account_id, role_id),
    KEY idx_account_roles_role (role_id, account_id),
    CONSTRAINT fk_account_roles_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE CASCADE,
    CONSTRAINT fk_account_roles_role
        FOREIGN KEY (role_id) REFERENCES roles (role_id),
    CONSTRAINT fk_account_roles_assigned_by
        FOREIGN KEY (assigned_by_account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE role_permissions (
    role_id             BIGINT UNSIGNED NOT NULL,
    permission_id       BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    KEY idx_role_permissions_permission (permission_id, role_id),
    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_id) REFERENCES roles (role_id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_id) REFERENCES permissions (permission_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE menus (
    menu_id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_menu_id      BIGINT UNSIGNED NULL,
    menu_code           VARCHAR(50) NOT NULL,
    menu_name           VARCHAR(100) NOT NULL,
    route_path          VARCHAR(255) NULL,
    icon_name           VARCHAR(100) NULL,
    sort_order          INT NOT NULL DEFAULT 0,
    visible             BOOLEAN NOT NULL DEFAULT TRUE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (menu_id),
    UNIQUE KEY uk_menus_code (menu_code),
    KEY idx_menus_parent_order (parent_menu_id, sort_order),
    CONSTRAINT fk_menus_parent
        FOREIGN KEY (parent_menu_id) REFERENCES menus (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE menu_permissions (
    menu_id             BIGINT UNSIGNED NOT NULL,
    permission_id       BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (menu_id, permission_id),
    KEY idx_menu_permissions_permission (permission_id),
    CONSTRAINT fk_menu_permissions_menu
        FOREIGN KEY (menu_id) REFERENCES menus (menu_id) ON DELETE CASCADE,
    CONSTRAINT fk_menu_permissions_permission
        FOREIGN KEY (permission_id) REFERENCES permissions (permission_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_menu_favorites (
    account_id          BIGINT UNSIGNED NOT NULL,
    menu_id             BIGINT UNSIGNED NOT NULL,
    sort_order          INT NOT NULL DEFAULT 0,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id, menu_id),
    CONSTRAINT fk_menu_favorites_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE CASCADE,
    CONSTRAINT fk_menu_favorites_menu
        FOREIGN KEY (menu_id) REFERENCES menus (menu_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_menu_visits (
    menu_visit_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id          BIGINT UNSIGNED NOT NULL,
    menu_id             BIGINT UNSIGNED NOT NULL,
    visited_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (menu_visit_id),
    KEY idx_menu_visits_account_time (account_id, visited_at),
    CONSTRAINT fk_menu_visits_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE CASCADE,
    CONSTRAINT fk_menu_visits_menu
        FOREIGN KEY (menu_id) REFERENCES menus (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_ui_preferences (
    account_id          BIGINT UNSIGNED NOT NULL,
    theme               VARCHAR(20) NOT NULL DEFAULT 'SYSTEM' COMMENT 'LIGHT, DARK, SYSTEM',
    locale              VARCHAR(20) NOT NULL DEFAULT 'ko-KR',
    timezone            VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul',
    page_size           INT UNSIGNED NOT NULL DEFAULT 20,
    sidebar_collapsed   BOOLEAN NOT NULL DEFAULT FALSE,
    dashboard_layout    JSON NULL,
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id),
    CONSTRAINT fk_ui_preferences_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE CASCADE,
    CONSTRAINT ck_ui_preferences_page_size CHECK (page_size BETWEEN 5 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Compatible with Spring Security JdbcTokenRepositoryImpl.
CREATE TABLE persistent_logins (
    username            VARCHAR(100) NOT NULL,
    series              VARCHAR(64) NOT NULL,
    token               VARCHAR(64) NOT NULL,
    last_used           DATETIME(6) NOT NULL,
    PRIMARY KEY (series),
    KEY idx_persistent_logins_username (username),
    CONSTRAINT fk_persistent_logins_username
        FOREIGN KEY (username) REFERENCES accounts (login_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_tokens (
    reset_token_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id          BIGINT UNSIGNED NOT NULL,
    token_hash          CHAR(64) NOT NULL COMMENT 'SHA-256 hash of the random token',
    expires_at          DATETIME(6) NOT NULL,
    used_at             DATETIME(6) NULL,
    requested_ip        VARCHAR(45) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (reset_token_id),
    UNIQUE KEY uk_password_reset_token_hash (token_hash),
    KEY idx_password_reset_account_expiry (account_id, expires_at),
    CONSTRAINT fk_password_reset_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE password_histories (
    password_history_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id          BIGINT UNSIGNED NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (password_history_id),
    KEY idx_password_histories_account_time (account_id, created_at),
    CONSTRAINT fk_password_histories_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE account_mfa_settings (
    account_id          BIGINT UNSIGNED NOT NULL,
    mfa_type            VARCHAR(20) NOT NULL DEFAULT 'TOTP',
    encrypted_secret    VARBINARY(512) NOT NULL,
    backup_codes_hash   JSON NULL,
    enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at         DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id),
    CONSTRAINT fk_mfa_settings_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE login_histories (
    login_history_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id          BIGINT UNSIGNED NULL,
    attempted_login_id  VARCHAR(100) NOT NULL,
    success             BOOLEAN NOT NULL,
    failure_reason      VARCHAR(50) NULL,
    ip_address          VARCHAR(45) NULL,
    user_agent          VARCHAR(1000) NULL,
    session_id_hash     CHAR(64) NULL,
    logged_in_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    logged_out_at       DATETIME(6) NULL,
    PRIMARY KEY (login_history_id),
    KEY idx_login_histories_account_time (account_id, logged_in_at),
    KEY idx_login_histories_login_time (attempted_login_id, logged_in_at),
    KEY idx_login_histories_ip_time (ip_address, logged_in_at),
    CONSTRAINT fk_login_histories_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE active_sessions (
    session_id          VARCHAR(128) NOT NULL,
    account_id          BIGINT UNSIGNED NOT NULL,
    ip_address          VARCHAR(45) NULL,
    user_agent          VARCHAR(1000) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_accessed_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at          DATETIME(6) NOT NULL,
    revoked_at          DATETIME(6) NULL,
    PRIMARY KEY (session_id),
    KEY idx_active_sessions_account (account_id, expires_at),
    CONSTRAINT fk_active_sessions_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- 3. Common file management
-- ============================================================================

CREATE TABLE file_assets (
    file_id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    uploader_account_id BIGINT UNSIGNED NOT NULL,
    original_name       VARCHAR(255) NOT NULL,
    stored_name         VARCHAR(255) NOT NULL,
    storage_provider    VARCHAR(20) NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL, S3, MINIO',
    storage_key         VARCHAR(700) NOT NULL,
    content_type        VARCHAR(150) NOT NULL,
    extension           VARCHAR(20) NULL,
    size_bytes          BIGINT UNSIGNED NOT NULL,
    checksum_sha256     CHAR(64) NULL,
    image_width         INT UNSIGNED NULL,
    image_height        INT UNSIGNED NULL,
    download_count      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    scan_status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         COMMENT 'PENDING, SAFE, INFECTED, FAILED',
    deleted_at          DATETIME(6) NULL,
    deleted_by_account_id BIGINT UNSIGNED NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (file_id),
    UNIQUE KEY uk_file_assets_storage_key (storage_key),
    KEY idx_file_assets_uploader_time (uploader_account_id, created_at),
    KEY idx_file_assets_content_type (company_id, content_type, deleted_at),
    KEY idx_file_assets_checksum (checksum_sha256),
    CONSTRAINT fk_file_assets_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_file_assets_uploader
        FOREIGN KEY (uploader_account_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_file_assets_deleted_by
        FOREIGN KEY (deleted_by_account_id) REFERENCES accounts (account_id),
    CONSTRAINT ck_file_assets_size CHECK (size_bytes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE employees
    ADD CONSTRAINT fk_employees_profile_file
        FOREIGN KEY (profile_file_id) REFERENCES file_assets (file_id);

CREATE TABLE file_access_logs (
    file_access_log_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    file_id             BIGINT UNSIGNED NOT NULL,
    account_id          BIGINT UNSIGNED NULL,
    action_type         VARCHAR(20) NOT NULL COMMENT 'DOWNLOAD, PREVIEW, DELETE, RESTORE',
    ip_address          VARCHAR(45) NULL,
    occurred_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (file_access_log_id),
    KEY idx_file_access_file_time (file_id, occurred_at),
    KEY idx_file_access_account_time (account_id, occurred_at),
    CONSTRAINT fk_file_access_file
        FOREIGN KEY (file_id) REFERENCES file_assets (file_id),
    CONSTRAINT fk_file_access_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- 4. Electronic approval
-- ============================================================================

CREATE TABLE approval_templates (
    approval_template_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    template_code       VARCHAR(50) NOT NULL,
    template_name       VARCHAR(100) NOT NULL,
    document_type       VARCHAR(30) NOT NULL COMMENT 'GENERAL, LEAVE, ATTENDANCE_CORRECTION',
    description         VARCHAR(500) NULL,
    body_schema         JSON NULL COMMENT 'Optional dynamic form definition',
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_account_id BIGINT UNSIGNED NOT NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (approval_template_id),
    UNIQUE KEY uk_approval_templates_code (company_id, template_code),
    KEY idx_approval_templates_type (company_id, document_type, active),
    CONSTRAINT fk_approval_templates_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_approval_templates_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE approval_template_steps (
    approval_template_step_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    approval_template_id BIGINT UNSIGNED NOT NULL,
    step_order          INT UNSIGNED NOT NULL,
    step_type           VARCHAR(20) NOT NULL DEFAULT 'APPROVAL'
                         COMMENT 'APPROVAL, AGREEMENT, REFERENCE',
    approver_rule       VARCHAR(30) NOT NULL
                         COMMENT 'EMPLOYEE, ROLE, DRAFTER_MANAGER, DEPARTMENT_LEADER',
    approver_employee_id BIGINT UNSIGNED NULL,
    approver_role_id    BIGINT UNSIGNED NULL,
    required            BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (approval_template_step_id),
    UNIQUE KEY uk_approval_template_steps_order (approval_template_id, step_order),
    CONSTRAINT fk_approval_template_steps_template
        FOREIGN KEY (approval_template_id) REFERENCES approval_templates (approval_template_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_approval_template_steps_employee
        FOREIGN KEY (approver_employee_id) REFERENCES employees (employee_id),
    CONSTRAINT fk_approval_template_steps_role
        FOREIGN KEY (approver_role_id) REFERENCES roles (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE approval_documents (
    approval_document_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    approval_template_id BIGINT UNSIGNED NULL,
    drafter_employee_id BIGINT UNSIGNED NOT NULL,
    document_number     VARCHAR(50) NULL,
    title               VARCHAR(200) NOT NULL,
    content             LONGTEXT NULL,
    form_data           JSON NULL,
    document_type       VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    business_type       VARCHAR(50) NULL COMMENT 'LEAVE_REQUEST, ATTENDANCE_CORRECTION, etc.',
    business_id         BIGINT UNSIGNED NULL COMMENT 'Polymorphic business record id',
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                         COMMENT 'DRAFT, PENDING, APPROVED, REJECTED, CANCELED',
    current_step_order  INT UNSIGNED NULL,
    submitted_at        DATETIME(6) NULL,
    completed_at        DATETIME(6) NULL,
    canceled_at         DATETIME(6) NULL,
    cancel_reason       VARCHAR(500) NULL,
    deleted_at          DATETIME(6) NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (approval_document_id),
    UNIQUE KEY uk_approval_documents_number (company_id, document_number),
    KEY idx_approval_documents_drafter (drafter_employee_id, status, created_at),
    KEY idx_approval_documents_status_time (company_id, status, submitted_at),
    KEY idx_approval_documents_business (business_type, business_id),
    KEY idx_approval_documents_title (company_id, title),
    FULLTEXT KEY ftx_approval_documents_title_content (title, content),
    CONSTRAINT fk_approval_documents_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_approval_documents_template
        FOREIGN KEY (approval_template_id) REFERENCES approval_templates (approval_template_id),
    CONSTRAINT fk_approval_documents_drafter
        FOREIGN KEY (drafter_employee_id) REFERENCES employees (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE approval_lines (
    approval_line_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    approval_document_id BIGINT UNSIGNED NOT NULL,
    step_order          INT UNSIGNED NOT NULL,
    line_type           VARCHAR(20) NOT NULL DEFAULT 'APPROVAL'
                         COMMENT 'APPROVAL, AGREEMENT, REFERENCE',
    approver_employee_id BIGINT UNSIGNED NOT NULL,
    delegated_by_employee_id BIGINT UNSIGNED NULL,
    required            BOOLEAN NOT NULL DEFAULT TRUE,
    status              VARCHAR(20) NOT NULL DEFAULT 'WAITING'
                         COMMENT 'WAITING, PENDING, APPROVED, REJECTED, SKIPPED, CANCELED',
    opinion             VARCHAR(2000) NULL,
    rejection_reason    VARCHAR(1000) NULL,
    requested_at        DATETIME(6) NULL,
    acted_at            DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (approval_line_id),
    UNIQUE KEY uk_approval_lines_order (approval_document_id, step_order),
    KEY idx_approval_lines_approver_status (approver_employee_id, status, requested_at),
    CONSTRAINT fk_approval_lines_document
        FOREIGN KEY (approval_document_id) REFERENCES approval_documents (approval_document_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_approval_lines_approver
        FOREIGN KEY (approver_employee_id) REFERENCES employees (employee_id),
    CONSTRAINT fk_approval_lines_delegated_by
        FOREIGN KEY (delegated_by_employee_id) REFERENCES employees (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE approval_document_attachments (
    approval_document_id BIGINT UNSIGNED NOT NULL,
    file_id             BIGINT UNSIGNED NOT NULL,
    sort_order          INT NOT NULL DEFAULT 0,
    PRIMARY KEY (approval_document_id, file_id),
    CONSTRAINT fk_approval_attachments_document
        FOREIGN KEY (approval_document_id) REFERENCES approval_documents (approval_document_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_approval_attachments_file
        FOREIGN KEY (file_id) REFERENCES file_assets (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE approval_histories (
    approval_history_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    approval_document_id BIGINT UNSIGNED NOT NULL,
    approval_line_id    BIGINT UNSIGNED NULL,
    actor_employee_id   BIGINT UNSIGNED NOT NULL,
    action_type         VARCHAR(30) NOT NULL
                         COMMENT 'CREATE, SUBMIT, APPROVE, REJECT, CANCEL, DELEGATE, COMMENT',
    from_status         VARCHAR(20) NULL,
    to_status           VARCHAR(20) NULL,
    comment_text        VARCHAR(2000) NULL,
    acted_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (approval_history_id),
    KEY idx_approval_histories_document_time (approval_document_id, acted_at),
    CONSTRAINT fk_approval_histories_document
        FOREIGN KEY (approval_document_id) REFERENCES approval_documents (approval_document_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_approval_histories_line
        FOREIGN KEY (approval_line_id) REFERENCES approval_lines (approval_line_id),
    CONSTRAINT fk_approval_histories_actor
        FOREIGN KEY (actor_employee_id) REFERENCES employees (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- 5. Annual leave
-- ============================================================================

CREATE TABLE leave_policies (
    leave_policy_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    policy_name         VARCHAR(100) NOT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE NULL,
    base_annual_days    DECIMAL(5,2) NOT NULL DEFAULT 15.00,
    first_year_monthly_days DECIMAL(5,2) NOT NULL DEFAULT 1.00,
    max_first_year_days DECIMAL(5,2) NOT NULL DEFAULT 11.00,
    additional_days_per_years INT UNSIGNED NOT NULL DEFAULT 2,
    max_additional_days DECIMAL(5,2) NOT NULL DEFAULT 10.00,
    max_carry_over_days DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    carry_over_expiry_month INT UNSIGNED NULL,
    standard_day_hours DECIMAL(4,2) NOT NULL DEFAULT 8.00,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (leave_policy_id),
    UNIQUE KEY uk_leave_policies_name_date (company_id, policy_name, effective_from),
    KEY idx_leave_policies_effective (company_id, active, effective_from, effective_to),
    CONSTRAINT fk_leave_policies_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT ck_leave_policies_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_leave_policies_days
        CHECK (base_annual_days >= 0 AND first_year_monthly_days >= 0
               AND max_first_year_days >= 0 AND max_carry_over_days >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_types (
    leave_type_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    leave_type_code     VARCHAR(30) NOT NULL,
    leave_type_name     VARCHAR(50) NOT NULL,
    deducts_annual_leave BOOLEAN NOT NULL DEFAULT TRUE,
    requires_reason     BOOLEAN NOT NULL DEFAULT TRUE,
    requires_attachment BOOLEAN NOT NULL DEFAULT FALSE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order          INT NOT NULL DEFAULT 0,
    PRIMARY KEY (leave_type_id),
    UNIQUE KEY uk_leave_types_code (company_id, leave_type_code),
    CONSTRAINT fk_leave_types_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE company_holidays (
    holiday_id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    holiday_date        DATE NOT NULL,
    holiday_name        VARCHAR(100) NOT NULL,
    paid_holiday        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (holiday_id),
    UNIQUE KEY uk_company_holidays_date (company_id, holiday_date),
    CONSTRAINT fk_company_holidays_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_balances (
    leave_balance_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT UNSIGNED NOT NULL,
    leave_policy_id     BIGINT UNSIGNED NOT NULL,
    balance_year        SMALLINT UNSIGNED NOT NULL,
    granted_days        DECIMAL(6,2) NOT NULL DEFAULT 0.00,
    carried_over_days   DECIMAL(6,2) NOT NULL DEFAULT 0.00,
    adjustment_days     DECIMAL(6,2) NOT NULL DEFAULT 0.00,
    used_days           DECIMAL(6,2) NOT NULL DEFAULT 0.00,
    pending_days        DECIMAL(6,2) NOT NULL DEFAULT 0.00,
    expired_days        DECIMAL(6,2) NOT NULL DEFAULT 0.00,
    remaining_days      DECIMAL(7,2) AS
        (granted_days + carried_over_days + adjustment_days - used_days - expired_days) STORED,
    available_days      DECIMAL(7,2) AS
        (granted_days + carried_over_days + adjustment_days - used_days
         - pending_days - expired_days) STORED,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (leave_balance_id),
    UNIQUE KEY uk_leave_balances_employee_year (employee_id, balance_year),
    KEY idx_leave_balances_year (balance_year, employee_id),
    CONSTRAINT fk_leave_balances_employee
        FOREIGN KEY (employee_id) REFERENCES employees (employee_id),
    CONSTRAINT fk_leave_balances_policy
        FOREIGN KEY (leave_policy_id) REFERENCES leave_policies (leave_policy_id),
    CONSTRAINT ck_leave_balances_nonnegative
        CHECK (granted_days >= 0 AND carried_over_days >= 0 AND used_days >= 0
               AND pending_days >= 0 AND expired_days >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_requests (
    leave_request_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT UNSIGNED NOT NULL,
    leave_type_id       BIGINT UNSIGNED NOT NULL,
    leave_balance_id    BIGINT UNSIGNED NULL,
    approval_document_id BIGINT UNSIGNED NULL,
    leave_unit          VARCHAR(20) NOT NULL DEFAULT 'DAY'
                         COMMENT 'DAY, HALF_AM, HALF_PM, HOUR',
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    start_time          TIME NULL,
    end_time            TIME NULL,
    requested_days      DECIMAL(5,2) NOT NULL,
    reason              VARCHAR(1000) NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                         COMMENT 'DRAFT, PENDING, APPROVED, REJECTED, CANCELED',
    rejection_reason    VARCHAR(1000) NULL,
    submitted_at        DATETIME(6) NULL,
    approved_at         DATETIME(6) NULL,
    canceled_at         DATETIME(6) NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (leave_request_id),
    UNIQUE KEY uk_leave_requests_approval_document (approval_document_id),
    KEY idx_leave_requests_employee_status (employee_id, status, start_date),
    KEY idx_leave_requests_period (start_date, end_date, status),
    KEY idx_leave_requests_balance (leave_balance_id),
    CONSTRAINT fk_leave_requests_employee
        FOREIGN KEY (employee_id) REFERENCES employees (employee_id),
    CONSTRAINT fk_leave_requests_type
        FOREIGN KEY (leave_type_id) REFERENCES leave_types (leave_type_id),
    CONSTRAINT fk_leave_requests_balance
        FOREIGN KEY (leave_balance_id) REFERENCES leave_balances (leave_balance_id),
    CONSTRAINT fk_leave_requests_approval
        FOREIGN KEY (approval_document_id) REFERENCES approval_documents (approval_document_id),
    CONSTRAINT ck_leave_requests_period CHECK (end_date >= start_date),
    CONSTRAINT ck_leave_requests_days CHECK (requested_days > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_request_attachments (
    leave_request_id    BIGINT UNSIGNED NOT NULL,
    file_id             BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (leave_request_id, file_id),
    CONSTRAINT fk_leave_attachments_request
        FOREIGN KEY (leave_request_id) REFERENCES leave_requests (leave_request_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_leave_attachments_file
        FOREIGN KEY (file_id) REFERENCES file_assets (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_transactions (
    leave_transaction_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    leave_balance_id    BIGINT UNSIGNED NOT NULL,
    leave_request_id    BIGINT UNSIGNED NULL,
    transaction_type    VARCHAR(20) NOT NULL
                         COMMENT 'GRANT, CARRY_OVER, USE, RESTORE, ADJUST, EXPIRE',
    idempotency_key     VARCHAR(100) NULL
                         COMMENT 'Prevents duplicate scheduled grants, e.g. 2026-01-MONTHLY',
    days                DECIMAL(6,2) NOT NULL COMMENT 'Signed amount: grant +, use/expire -',
    effective_date      DATE NOT NULL,
    description         VARCHAR(500) NULL,
    created_by_account_id BIGINT UNSIGNED NULL COMMENT 'NULL for scheduled automatic accrual',
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (leave_transaction_id),
    UNIQUE KEY uk_leave_transactions_idempotency (leave_balance_id, idempotency_key),
    KEY idx_leave_transactions_balance_date (leave_balance_id, effective_date),
    KEY idx_leave_transactions_request (leave_request_id),
    CONSTRAINT fk_leave_transactions_balance
        FOREIGN KEY (leave_balance_id) REFERENCES leave_balances (leave_balance_id),
    CONSTRAINT fk_leave_transactions_request
        FOREIGN KEY (leave_request_id) REFERENCES leave_requests (leave_request_id),
    CONSTRAINT fk_leave_transactions_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES accounts (account_id),
    CONSTRAINT ck_leave_transactions_days CHECK (days <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- 6. Attendance
-- ============================================================================

CREATE TABLE work_policies (
    work_policy_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    policy_name         VARCHAR(100) NOT NULL,
    work_start_time     TIME NOT NULL DEFAULT '09:00:00',
    work_end_time       TIME NOT NULL DEFAULT '18:00:00',
    break_minutes       INT UNSIGNED NOT NULL DEFAULT 60,
    late_grace_minutes  INT UNSIGNED NOT NULL DEFAULT 0,
    early_leave_grace_minutes INT UNSIGNED NOT NULL DEFAULT 0,
    standard_work_minutes INT UNSIGNED NOT NULL DEFAULT 480,
    work_days_mask      CHAR(7) NOT NULL DEFAULT '1111100'
                         COMMENT 'Monday through Sunday; 1 means workday',
    effective_from      DATE NOT NULL,
    effective_to        DATE NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (work_policy_id),
    UNIQUE KEY uk_work_policies_name_date (company_id, policy_name, effective_from),
    KEY idx_work_policies_effective (company_id, active, effective_from, effective_to),
    CONSTRAINT fk_work_policies_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT ck_work_policies_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE employee_work_policy_assignments (
    work_assignment_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT UNSIGNED NOT NULL,
    work_policy_id      BIGINT UNSIGNED NOT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (work_assignment_id),
    UNIQUE KEY uk_work_assignments_employee_from (employee_id, effective_from),
    KEY idx_work_assignments_policy (work_policy_id, effective_from),
    CONSTRAINT fk_work_assignments_employee
        FOREIGN KEY (employee_id) REFERENCES employees (employee_id),
    CONSTRAINT fk_work_assignments_policy
        FOREIGN KEY (work_policy_id) REFERENCES work_policies (work_policy_id),
    CONSTRAINT ck_work_assignments_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE qr_attendance_terminals (
    qr_terminal_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    terminal_code       VARCHAR(50) NOT NULL,
    terminal_name       VARCHAR(100) NOT NULL,
    location_name       VARCHAR(200) NULL,
    token_hash          CHAR(64) NOT NULL,
    token_expires_at    DATETIME(6) NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    last_rotated_at     DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (qr_terminal_id),
    UNIQUE KEY uk_qr_terminals_code (company_id, terminal_code),
    UNIQUE KEY uk_qr_terminals_token (token_hash),
    CONSTRAINT fk_qr_terminals_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE attendance_records (
    attendance_record_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT UNSIGNED NOT NULL,
    work_policy_id      BIGINT UNSIGNED NULL,
    work_date           DATE NOT NULL,
    scheduled_start_at  DATETIME(6) NULL,
    scheduled_end_at    DATETIME(6) NULL,
    clock_in_at         DATETIME(6) NULL,
    clock_out_at        DATETIME(6) NULL,
    break_minutes       INT UNSIGNED NOT NULL DEFAULT 0,
    work_minutes        INT UNSIGNED NOT NULL DEFAULT 0,
    overtime_minutes    INT UNSIGNED NOT NULL DEFAULT 0,
    late_minutes        INT UNSIGNED NOT NULL DEFAULT 0,
    early_leave_minutes INT UNSIGNED NOT NULL DEFAULT 0,
    status              VARCHAR(30) NOT NULL DEFAULT 'ABSENT'
                         COMMENT 'NORMAL, LATE, EARLY_LEAVE, LATE_EARLY, ABSENT, LEAVE, HOLIDAY',
    note                VARCHAR(500) NULL,
    confirmed           BOOLEAN NOT NULL DEFAULT FALSE,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (attendance_record_id),
    UNIQUE KEY uk_attendance_records_employee_date (employee_id, work_date),
    KEY idx_attendance_records_date_status (work_date, status),
    KEY idx_attendance_records_clock_in (work_date, clock_in_at),
    CONSTRAINT fk_attendance_records_employee
        FOREIGN KEY (employee_id) REFERENCES employees (employee_id),
    CONSTRAINT fk_attendance_records_policy
        FOREIGN KEY (work_policy_id) REFERENCES work_policies (work_policy_id),
    CONSTRAINT ck_attendance_records_clock
        CHECK (clock_out_at IS NULL OR clock_in_at IS NULL OR clock_out_at >= clock_in_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE attendance_events (
    attendance_event_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    attendance_record_id BIGINT UNSIGNED NULL,
    employee_id         BIGINT UNSIGNED NOT NULL,
    qr_terminal_id      BIGINT UNSIGNED NULL,
    event_type          VARCHAR(20) NOT NULL COMMENT 'CLOCK_IN, CLOCK_OUT',
    event_source        VARCHAR(20) NOT NULL DEFAULT 'WEB' COMMENT 'WEB, MOBILE, QR, ADMIN',
    occurred_at         DATETIME(6) NOT NULL,
    ip_address          VARCHAR(45) NULL,
    latitude            DECIMAL(10,7) NULL,
    longitude           DECIMAL(10,7) NULL,
    user_agent          VARCHAR(1000) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (attendance_event_id),
    KEY idx_attendance_events_employee_time (employee_id, occurred_at),
    KEY idx_attendance_events_record (attendance_record_id, occurred_at),
    CONSTRAINT fk_attendance_events_record
        FOREIGN KEY (attendance_record_id) REFERENCES attendance_records (attendance_record_id),
    CONSTRAINT fk_attendance_events_employee
        FOREIGN KEY (employee_id) REFERENCES employees (employee_id),
    CONSTRAINT fk_attendance_events_terminal
        FOREIGN KEY (qr_terminal_id) REFERENCES qr_attendance_terminals (qr_terminal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE attendance_correction_requests (
    correction_request_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    attendance_record_id BIGINT UNSIGNED NOT NULL,
    requester_employee_id BIGINT UNSIGNED NOT NULL,
    approval_document_id BIGINT UNSIGNED NULL,
    requested_clock_in_at DATETIME(6) NULL,
    requested_clock_out_at DATETIME(6) NULL,
    reason              VARCHAR(1000) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         COMMENT 'PENDING, APPROVED, REJECTED, CANCELED',
    processed_at        DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (correction_request_id),
    UNIQUE KEY uk_attendance_corrections_approval (approval_document_id),
    KEY idx_attendance_corrections_requester (requester_employee_id, status, created_at),
    CONSTRAINT fk_attendance_corrections_record
        FOREIGN KEY (attendance_record_id) REFERENCES attendance_records (attendance_record_id),
    CONSTRAINT fk_attendance_corrections_requester
        FOREIGN KEY (requester_employee_id) REFERENCES employees (employee_id),
    CONSTRAINT fk_attendance_corrections_approval
        FOREIGN KEY (approval_document_id) REFERENCES approval_documents (approval_document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- 7. Notices and resource library
-- ============================================================================

CREATE TABLE notice_categories (
    notice_category_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    category_name       VARCHAR(50) NOT NULL,
    sort_order          INT NOT NULL DEFAULT 0,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (notice_category_id),
    UNIQUE KEY uk_notice_categories_name (company_id, category_name),
    CONSTRAINT fk_notice_categories_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notices (
    notice_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    notice_category_id  BIGINT UNSIGNED NULL,
    author_account_id   BIGINT UNSIGNED NOT NULL,
    title               VARCHAR(200) NOT NULL,
    content             LONGTEXT NOT NULL,
    pinned              BOOLEAN NOT NULL DEFAULT FALSE,
    pin_started_at      DATETIME(6) NULL,
    pin_ended_at        DATETIME(6) NULL,
    published           BOOLEAN NOT NULL DEFAULT FALSE,
    published_at        DATETIME(6) NULL,
    view_count          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    deleted_at          DATETIME(6) NULL,
    deleted_by_account_id BIGINT UNSIGNED NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (notice_id),
    KEY idx_notices_list (company_id, published, pinned, published_at, deleted_at),
    KEY idx_notices_category (notice_category_id, published_at),
    KEY idx_notices_author (author_account_id, created_at),
    KEY idx_notices_title (company_id, title),
    FULLTEXT KEY ftx_notices_title_content (title, content),
    CONSTRAINT fk_notices_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_notices_category
        FOREIGN KEY (notice_category_id) REFERENCES notice_categories (notice_category_id),
    CONSTRAINT fk_notices_author
        FOREIGN KEY (author_account_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_notices_deleted_by
        FOREIGN KEY (deleted_by_account_id) REFERENCES accounts (account_id),
    CONSTRAINT ck_notices_pin_period
        CHECK (pin_ended_at IS NULL OR pin_started_at IS NULL OR pin_ended_at >= pin_started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notice_attachments (
    notice_id           BIGINT UNSIGNED NOT NULL,
    file_id             BIGINT UNSIGNED NOT NULL,
    sort_order          INT NOT NULL DEFAULT 0,
    PRIMARY KEY (notice_id, file_id),
    CONSTRAINT fk_notice_attachments_notice
        FOREIGN KEY (notice_id) REFERENCES notices (notice_id) ON DELETE CASCADE,
    CONSTRAINT fk_notice_attachments_file
        FOREIGN KEY (file_id) REFERENCES file_assets (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notice_reads (
    notice_id           BIGINT UNSIGNED NOT NULL,
    account_id          BIGINT UNSIGNED NOT NULL,
    first_read_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_read_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_count          INT UNSIGNED NOT NULL DEFAULT 1,
    PRIMARY KEY (notice_id, account_id),
    KEY idx_notice_reads_account_time (account_id, last_read_at),
    CONSTRAINT fk_notice_reads_notice
        FOREIGN KEY (notice_id) REFERENCES notices (notice_id) ON DELETE CASCADE,
    CONSTRAINT fk_notice_reads_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resource_folders (
    resource_folder_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    parent_folder_id    BIGINT UNSIGNED NULL,
    folder_name         VARCHAR(100) NOT NULL,
    sort_order          INT NOT NULL DEFAULT 0,
    created_by_account_id BIGINT UNSIGNED NOT NULL,
    deleted_at          DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (resource_folder_id),
    KEY idx_resource_folders_parent (company_id, parent_folder_id, sort_order),
    CONSTRAINT fk_resource_folders_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_resource_folders_parent
        FOREIGN KEY (parent_folder_id) REFERENCES resource_folders (resource_folder_id),
    CONSTRAINT fk_resource_folders_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resources (
    resource_id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    resource_folder_id  BIGINT UNSIGNED NULL,
    author_account_id   BIGINT UNSIGNED NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         LONGTEXT NULL,
    download_count      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    published           BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at          DATETIME(6) NULL,
    deleted_by_account_id BIGINT UNSIGNED NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (resource_id),
    KEY idx_resources_list (company_id, resource_folder_id, published, created_at, deleted_at),
    KEY idx_resources_title (company_id, title),
    FULLTEXT KEY ftx_resources_title_description (title, description),
    CONSTRAINT fk_resources_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_resources_folder
        FOREIGN KEY (resource_folder_id) REFERENCES resource_folders (resource_folder_id),
    CONSTRAINT fk_resources_author
        FOREIGN KEY (author_account_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_resources_deleted_by
        FOREIGN KEY (deleted_by_account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resource_files (
    resource_id         BIGINT UNSIGNED NOT NULL,
    file_id             BIGINT UNSIGNED NOT NULL,
    sort_order          INT NOT NULL DEFAULT 0,
    PRIMARY KEY (resource_id, file_id),
    CONSTRAINT fk_resource_files_resource
        FOREIGN KEY (resource_id) REFERENCES resources (resource_id) ON DELETE CASCADE,
    CONSTRAINT fk_resource_files_file
        FOREIGN KEY (file_id) REFERENCES file_assets (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resource_download_histories (
    resource_download_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    resource_id         BIGINT UNSIGNED NOT NULL,
    file_id             BIGINT UNSIGNED NOT NULL,
    account_id          BIGINT UNSIGNED NOT NULL,
    ip_address          VARCHAR(45) NULL,
    downloaded_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (resource_download_id),
    KEY idx_resource_downloads_resource_time (resource_id, downloaded_at),
    KEY idx_resource_downloads_account_time (account_id, downloaded_at),
    CONSTRAINT fk_resource_downloads_resource
        FOREIGN KEY (resource_id) REFERENCES resources (resource_id),
    CONSTRAINT fk_resource_downloads_file
        FOREIGN KEY (file_id) REFERENCES file_assets (file_id),
    CONSTRAINT fk_resource_downloads_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- 8. Calendar and schedules
-- ============================================================================

CREATE TABLE calendar_events (
    calendar_event_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    organizer_employee_id BIGINT UNSIGNED NOT NULL,
    event_type          VARCHAR(20) NOT NULL COMMENT 'MEETING, TRAINING, LEAVE, COMPANY_EVENT, ETC',
    title               VARCHAR(200) NOT NULL,
    description         LONGTEXT NULL,
    location            VARCHAR(255) NULL,
    starts_at           DATETIME(6) NOT NULL,
    ends_at             DATETIME(6) NOT NULL,
    all_day             BOOLEAN NOT NULL DEFAULT FALSE,
    visibility          VARCHAR(20) NOT NULL DEFAULT 'COMPANY'
                         COMMENT 'PRIVATE, PARTICIPANTS, DEPARTMENT, COMPANY',
    source_type         VARCHAR(30) NULL COMMENT 'LEAVE_REQUEST, APPROVAL_DOCUMENT, MANUAL',
    source_id           BIGINT UNSIGNED NULL,
    recurrence_rule     VARCHAR(1000) NULL COMMENT 'RFC 5545 RRULE',
    color               CHAR(7) NULL,
    canceled_at         DATETIME(6) NULL,
    deleted_at          DATETIME(6) NULL,
    version             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (calendar_event_id),
    KEY idx_calendar_events_period (company_id, starts_at, ends_at, deleted_at),
    KEY idx_calendar_events_organizer (organizer_employee_id, starts_at),
    KEY idx_calendar_events_source (source_type, source_id),
    CONSTRAINT fk_calendar_events_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_calendar_events_organizer
        FOREIGN KEY (organizer_employee_id) REFERENCES employees (employee_id),
    CONSTRAINT ck_calendar_events_period CHECK (ends_at >= starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE calendar_event_participants (
    calendar_event_id   BIGINT UNSIGNED NOT NULL,
    employee_id         BIGINT UNSIGNED NOT NULL,
    participant_role    VARCHAR(20) NOT NULL DEFAULT 'ATTENDEE' COMMENT 'ORGANIZER, ATTENDEE',
    response_status     VARCHAR(20) NOT NULL DEFAULT 'NEEDS_ACTION'
                         COMMENT 'NEEDS_ACTION, ACCEPTED, DECLINED, TENTATIVE',
    responded_at        DATETIME(6) NULL,
    PRIMARY KEY (calendar_event_id, employee_id),
    KEY idx_event_participants_employee (employee_id, response_status),
    CONSTRAINT fk_event_participants_event
        FOREIGN KEY (calendar_event_id) REFERENCES calendar_events (calendar_event_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_event_participants_employee
        FOREIGN KEY (employee_id) REFERENCES employees (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE calendar_event_attachments (
    calendar_event_id   BIGINT UNSIGNED NOT NULL,
    file_id             BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (calendar_event_id, file_id),
    CONSTRAINT fk_event_attachments_event
        FOREIGN KEY (calendar_event_id) REFERENCES calendar_events (calendar_event_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_event_attachments_file
        FOREIGN KEY (file_id) REFERENCES file_assets (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- 9. Internal messaging and notifications
-- ============================================================================

CREATE TABLE internal_messages (
    message_id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sender_account_id   BIGINT UNSIGNED NOT NULL,
    recipient_account_id BIGINT UNSIGNED NOT NULL,
    parent_message_id   BIGINT UNSIGNED NULL,
    subject             VARCHAR(200) NOT NULL,
    body                LONGTEXT NOT NULL,
    read_at             DATETIME(6) NULL,
    sender_deleted_at   DATETIME(6) NULL,
    recipient_deleted_at DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (message_id),
    KEY idx_messages_inbox (recipient_account_id, recipient_deleted_at, read_at, created_at),
    KEY idx_messages_sent (sender_account_id, sender_deleted_at, created_at),
    KEY idx_messages_parent (parent_message_id),
    CONSTRAINT fk_messages_sender
        FOREIGN KEY (sender_account_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_messages_recipient
        FOREIGN KEY (recipient_account_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_messages_parent
        FOREIGN KEY (parent_message_id) REFERENCES internal_messages (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE message_attachments (
    message_id          BIGINT UNSIGNED NOT NULL,
    file_id             BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (message_id, file_id),
    CONSTRAINT fk_message_attachments_message
        FOREIGN KEY (message_id) REFERENCES internal_messages (message_id) ON DELETE CASCADE,
    CONSTRAINT fk_message_attachments_file
        FOREIGN KEY (file_id) REFERENCES file_assets (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notifications (
    notification_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    recipient_account_id BIGINT UNSIGNED NOT NULL,
    actor_account_id    BIGINT UNSIGNED NULL,
    notification_type   VARCHAR(50) NOT NULL
                         COMMENT 'LEAVE_APPROVED, NOTICE_CREATED, APPROVAL_REQUESTED, APPROVAL_COMPLETED',
    title               VARCHAR(200) NOT NULL,
    message             VARCHAR(1000) NOT NULL,
    link_url            VARCHAR(500) NULL,
    reference_type      VARCHAR(50) NULL,
    reference_id        BIGINT UNSIGNED NULL,
    read_at             DATETIME(6) NULL,
    delivered_realtime_at DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (notification_id),
    KEY idx_notifications_recipient_read (recipient_account_id, read_at, created_at),
    KEY idx_notifications_reference (reference_type, reference_id),
    CONSTRAINT fk_notifications_recipient
        FOREIGN KEY (recipient_account_id) REFERENCES accounts (account_id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_actor
        FOREIGN KEY (actor_account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification_preferences (
    account_id          BIGINT UNSIGNED NOT NULL,
    notification_type   VARCHAR(50) NOT NULL,
    in_app_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id, notification_type),
    CONSTRAINT fk_notification_preferences_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE email_delivery_logs (
    email_delivery_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    notification_id     BIGINT UNSIGNED NULL,
    recipient_email     VARCHAR(255) NOT NULL,
    subject             VARCHAR(255) NOT NULL,
    template_code       VARCHAR(100) NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         COMMENT 'PENDING, SENDING, SENT, FAILED',
    retry_count         INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at       DATETIME(6) NULL,
    sent_at             DATETIME(6) NULL,
    error_message       VARCHAR(2000) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (email_delivery_id),
    KEY idx_email_delivery_status_retry (status, next_retry_at),
    KEY idx_email_delivery_recipient_time (recipient_email, created_at),
    CONSTRAINT fk_email_delivery_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_email_delivery_notification
        FOREIGN KEY (notification_id) REFERENCES notifications (notification_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- 10. Search, Excel jobs, audit, settings and operations
-- ============================================================================

CREATE TABLE search_histories (
    search_history_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id          BIGINT UNSIGNED NOT NULL,
    keyword             VARCHAR(255) NOT NULL,
    search_scope        VARCHAR(30) NOT NULL DEFAULT 'ALL'
                         COMMENT 'ALL, EMPLOYEE, NOTICE, RESOURCE, APPROVAL',
    result_count        INT UNSIGNED NOT NULL DEFAULT 0,
    searched_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (search_history_id),
    KEY idx_search_histories_account_time (account_id, searched_at),
    CONSTRAINT fk_search_histories_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE data_transfer_jobs (
    data_transfer_job_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    requested_by_account_id BIGINT UNSIGNED NOT NULL,
    job_type            VARCHAR(20) NOT NULL COMMENT 'IMPORT, EXPORT',
    target_type         VARCHAR(30) NOT NULL COMMENT 'EMPLOYEE, LEAVE, NOTICE',
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         COMMENT 'PENDING, RUNNING, COMPLETED, PARTIAL, FAILED',
    source_file_id      BIGINT UNSIGNED NULL,
    result_file_id      BIGINT UNSIGNED NULL,
    total_rows          INT UNSIGNED NOT NULL DEFAULT 0,
    success_rows        INT UNSIGNED NOT NULL DEFAULT 0,
    failed_rows         INT UNSIGNED NOT NULL DEFAULT 0,
    error_details       JSON NULL,
    started_at          DATETIME(6) NULL,
    completed_at        DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (data_transfer_job_id),
    KEY idx_data_transfer_jobs_requester (requested_by_account_id, created_at),
    KEY idx_data_transfer_jobs_status (status, created_at),
    CONSTRAINT fk_data_transfer_jobs_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_data_transfer_jobs_requester
        FOREIGN KEY (requested_by_account_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_data_transfer_jobs_source_file
        FOREIGN KEY (source_file_id) REFERENCES file_assets (file_id),
    CONSTRAINT fk_data_transfer_jobs_result_file
        FOREIGN KEY (result_file_id) REFERENCES file_assets (file_id),
    CONSTRAINT ck_data_transfer_job_rows
        CHECK (success_rows + failed_rows <= total_rows)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_logs (
    audit_log_id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NULL,
    actor_account_id    BIGINT UNSIGNED NULL,
    action_type         VARCHAR(30) NOT NULL COMMENT 'CREATE, UPDATE, DELETE, RESTORE, LOGIN, EXPORT',
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           VARCHAR(100) NULL,
    description         VARCHAR(500) NULL,
    before_data         JSON NULL,
    after_data          JSON NULL,
    changed_fields      JSON NULL,
    request_method      VARCHAR(10) NULL,
    request_uri         VARCHAR(1000) NULL,
    ip_address          VARCHAR(45) NULL,
    user_agent          VARCHAR(1000) NULL,
    trace_id            VARCHAR(100) NULL,
    occurred_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (audit_log_id),
    KEY idx_audit_logs_company_time (company_id, occurred_at),
    KEY idx_audit_logs_actor_time (actor_account_id, occurred_at),
    KEY idx_audit_logs_entity (entity_type, entity_id, occurred_at),
    KEY idx_audit_logs_trace (trace_id),
    CONSTRAINT fk_audit_logs_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_audit_logs_actor
        FOREIGN KEY (actor_account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE password_policies (
    company_id          BIGINT UNSIGNED NOT NULL,
    min_length          INT UNSIGNED NOT NULL DEFAULT 8,
    max_length          INT UNSIGNED NOT NULL DEFAULT 100,
    require_uppercase   BOOLEAN NOT NULL DEFAULT TRUE,
    require_lowercase   BOOLEAN NOT NULL DEFAULT TRUE,
    require_digit       BOOLEAN NOT NULL DEFAULT TRUE,
    require_special     BOOLEAN NOT NULL DEFAULT TRUE,
    expiry_days         INT UNSIGNED NOT NULL DEFAULT 90,
    history_count       INT UNSIGNED NOT NULL DEFAULT 5,
    max_failed_attempts INT UNSIGNED NOT NULL DEFAULT 5,
    lock_minutes        INT UNSIGNED NOT NULL DEFAULT 30,
    reset_token_minutes INT UNSIGNED NOT NULL DEFAULT 30,
    session_timeout_minutes INT UNSIGNED NOT NULL DEFAULT 30,
    updated_by_account_id BIGINT UNSIGNED NULL,
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (company_id),
    CONSTRAINT fk_password_policies_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_password_policies_updated_by
        FOREIGN KEY (updated_by_account_id) REFERENCES accounts (account_id),
    CONSTRAINT ck_password_policies_length CHECK (max_length >= min_length AND min_length >= 8)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE smtp_settings (
    company_id          BIGINT UNSIGNED NOT NULL,
    host                VARCHAR(255) NOT NULL,
    port                INT UNSIGNED NOT NULL DEFAULT 587,
    username            VARCHAR(255) NULL,
    encrypted_password  VARBINARY(1024) NULL,
    encryption_type     VARCHAR(20) NOT NULL DEFAULT 'STARTTLS' COMMENT 'NONE, SSL, STARTTLS',
    from_email          VARCHAR(255) NOT NULL,
    from_name           VARCHAR(100) NULL,
    connection_timeout_ms INT UNSIGNED NOT NULL DEFAULT 5000,
    enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    last_tested_at      DATETIME(6) NULL,
    last_test_success   BOOLEAN NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (company_id),
    CONSTRAINT fk_smtp_settings_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_smtp_settings_updated_by
        FOREIGN KEY (updated_by_account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE system_settings (
    system_setting_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    setting_group       VARCHAR(50) NOT NULL,
    setting_key         VARCHAR(100) NOT NULL,
    setting_value       TEXT NULL,
    value_type          VARCHAR(20) NOT NULL DEFAULT 'STRING'
                         COMMENT 'STRING, NUMBER, BOOLEAN, JSON',
    encrypted           BOOLEAN NOT NULL DEFAULT FALSE,
    description         VARCHAR(500) NULL,
    updated_by_account_id BIGINT UNSIGNED NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (system_setting_id),
    UNIQUE KEY uk_system_settings_key (company_id, setting_group, setting_key),
    CONSTRAINT fk_system_settings_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_system_settings_updated_by
        FOREIGN KEY (updated_by_account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE system_logs (
    system_log_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    log_level           VARCHAR(10) NOT NULL COMMENT 'TRACE, DEBUG, INFO, WARN, ERROR',
    logger_name         VARCHAR(255) NULL,
    message             TEXT NOT NULL,
    exception_class     VARCHAR(255) NULL,
    stack_trace         LONGTEXT NULL,
    trace_id            VARCHAR(100) NULL,
    account_id          BIGINT UNSIGNED NULL,
    request_uri         VARCHAR(1000) NULL,
    ip_address          VARCHAR(45) NULL,
    occurred_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (system_log_id),
    KEY idx_system_logs_level_time (log_level, occurred_at),
    KEY idx_system_logs_trace (trace_id),
    KEY idx_system_logs_account_time (account_id, occurred_at),
    CONSTRAINT fk_system_logs_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE system_metric_snapshots (
    metric_snapshot_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    instance_id         VARCHAR(100) NOT NULL,
    metric_name         VARCHAR(150) NOT NULL,
    metric_value        DECIMAL(24,6) NOT NULL,
    metric_unit         VARCHAR(30) NULL,
    tags_json           JSON NULL,
    collected_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (metric_snapshot_id),
    KEY idx_metric_snapshots_name_time (metric_name, collected_at),
    KEY idx_metric_snapshots_instance_time (instance_id, collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trash_items (
    trash_item_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    company_id          BIGINT UNSIGNED NOT NULL,
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           VARCHAR(100) NOT NULL,
    display_name        VARCHAR(255) NULL,
    deleted_by_account_id BIGINT UNSIGNED NOT NULL,
    deleted_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    purge_after         DATETIME(6) NULL,
    restored_by_account_id BIGINT UNSIGNED NULL,
    restored_at         DATETIME(6) NULL,
    PRIMARY KEY (trash_item_id),
    KEY idx_trash_items_entity (company_id, entity_type, entity_id, deleted_at),
    KEY idx_trash_items_purge (restored_at, purge_after),
    CONSTRAINT fk_trash_items_company
        FOREIGN KEY (company_id) REFERENCES companies (company_id),
    CONSTRAINT fk_trash_items_deleted_by
        FOREIGN KEY (deleted_by_account_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_trash_items_restored_by
        FOREIGN KEY (restored_by_account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- API access logs are deliberately separate from system error logs and immutable audit logs.
CREATE TABLE api_access_logs (
    api_access_log_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id          BIGINT UNSIGNED NULL,
    http_method         VARCHAR(10) NOT NULL,
    request_uri         VARCHAR(1000) NOT NULL,
    response_status     SMALLINT UNSIGNED NOT NULL,
    elapsed_ms          INT UNSIGNED NOT NULL,
    ip_address          VARCHAR(45) NULL,
    trace_id            VARCHAR(100) NULL,
    occurred_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (api_access_log_id),
    KEY idx_api_access_logs_time (occurred_at),
    KEY idx_api_access_logs_account_time (account_id, occurred_at),
    KEY idx_api_access_logs_status_time (response_status, occurred_at),
    KEY idx_api_access_logs_trace (trace_id),
    CONSTRAINT fk_api_access_logs_account
        FOREIGN KEY (account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- 11. Read models for dashboard and statistics
-- ============================================================================

CREATE OR REPLACE VIEW v_department_headcounts AS
SELECT
    d.company_id,
    d.department_id,
    d.department_code,
    d.department_name,
    COUNT(e.employee_id) AS employee_count
FROM departments d
LEFT JOIN employees e
       ON e.department_id = d.department_id
      AND e.employment_status <> 'RESIGNED'
      AND e.deleted_at IS NULL
WHERE d.active = TRUE
  AND d.deleted_at IS NULL
GROUP BY d.company_id, d.department_id, d.department_code, d.department_name;

CREATE OR REPLACE VIEW v_position_headcounts AS
SELECT
    p.company_id,
    p.position_id,
    p.position_code,
    p.position_name,
    p.position_level,
    COUNT(e.employee_id) AS employee_count
FROM positions p
LEFT JOIN employees e
       ON e.position_id = p.position_id
      AND e.employment_status <> 'RESIGNED'
      AND e.deleted_at IS NULL
WHERE p.active = TRUE
  AND p.deleted_at IS NULL
GROUP BY p.company_id, p.position_id, p.position_code, p.position_name, p.position_level;

CREATE OR REPLACE VIEW v_monthly_leave_usage AS
SELECT
    e.company_id,
    DATE_FORMAT(lt.effective_date, '%Y-%m') AS month_key,
    e.department_id,
    SUM(ABS(lt.days)) AS used_days,
    COUNT(DISTINCT lt.leave_request_id) AS request_count
FROM leave_transactions lt
JOIN leave_balances lb ON lb.leave_balance_id = lt.leave_balance_id
JOIN employees e ON e.employee_id = lb.employee_id
WHERE lt.transaction_type = 'USE'
GROUP BY e.company_id, DATE_FORMAT(lt.effective_date, '%Y-%m'), e.department_id;

CREATE OR REPLACE VIEW v_monthly_employment_movements AS
SELECT
    movements.company_id,
    movements.month_key,
    SUM(movements.hire_count) AS hire_count,
    SUM(movements.termination_count) AS termination_count
FROM (
    SELECT
        company_id,
        DATE_FORMAT(hire_date, '%Y-%m') AS month_key,
        COUNT(*) AS hire_count,
        0 AS termination_count
    FROM employees
    WHERE deleted_at IS NULL
    GROUP BY company_id, DATE_FORMAT(hire_date, '%Y-%m')

    UNION ALL

    SELECT
        company_id,
        DATE_FORMAT(termination_date, '%Y-%m') AS month_key,
        0 AS hire_count,
        COUNT(*) AS termination_count
    FROM employees
    WHERE termination_date IS NOT NULL
      AND deleted_at IS NULL
    GROUP BY company_id, DATE_FORMAT(termination_date, '%Y-%m')
) movements
GROUP BY movements.company_id, movements.month_key;

CREATE OR REPLACE VIEW v_daily_attendance_summary AS
SELECT
    e.company_id,
    ar.work_date,
    COUNT(*) AS target_employee_count,
    SUM(ar.clock_in_at IS NOT NULL) AS clocked_in_count,
    SUM(ar.status IN ('LATE', 'LATE_EARLY')) AS late_count,
    SUM(ar.status IN ('EARLY_LEAVE', 'LATE_EARLY')) AS early_leave_count,
    SUM(ar.status = 'LEAVE') AS leave_count,
    SUM(ar.status = 'ABSENT') AS absent_count
FROM attendance_records ar
JOIN employees e ON e.employee_id = ar.employee_id
GROUP BY e.company_id, ar.work_date;

CREATE OR REPLACE VIEW v_employee_leave_status AS
SELECT
    e.company_id,
    e.employee_id,
    e.employee_number,
    e.employee_name,
    lb.balance_year,
    lb.granted_days,
    lb.carried_over_days,
    lb.adjustment_days,
    lb.used_days,
    lb.pending_days,
    lb.expired_days,
    lb.remaining_days,
    lb.available_days
FROM employees e
JOIN leave_balances lb ON lb.employee_id = e.employee_id
WHERE e.deleted_at IS NULL;

-- ============================================================================
-- 12. Initial reference data
-- No initial administrator account is inserted here: create it through a bootstrap
-- command after BCrypt/Argon2 hashing the password, then remove that bootstrap path.
-- ============================================================================

INSERT INTO companies (company_code, company_name, timezone, locale)
VALUES ('OFFICE', '오피스 매니저', 'Asia/Seoul', 'ko-KR')
ON DUPLICATE KEY UPDATE
    company_name = VALUES(company_name),
    timezone = VALUES(timezone),
    locale = VALUES(locale);

SET @company_id := (
    SELECT company_id FROM companies WHERE company_code = 'OFFICE' LIMIT 1
);

INSERT INTO departments
    (company_id, department_code, department_name, sort_order)
VALUES
    (@company_id, 'DEV',   '개발팀',     10),
    (@company_id, 'SALES', '영업팀',     20),
    (@company_id, 'HR',    '인사팀',     30),
    (@company_id, 'RND',   '연구개발팀', 40)
ON DUPLICATE KEY UPDATE
    department_name = VALUES(department_name),
    sort_order = VALUES(sort_order),
    active = TRUE,
    deleted_at = NULL;

INSERT INTO positions
    (company_id, position_code, position_name, position_level, sort_order)
VALUES
    (@company_id, 'STAFF',     '사원', 10, 10),
    (@company_id, 'SENIOR',    '주임', 20, 20),
    (@company_id, 'ASSISTANT', '대리', 30, 30),
    (@company_id, 'MANAGER',   '과장', 40, 40),
    (@company_id, 'DEPUTY',    '차장', 50, 50),
    (@company_id, 'GENERAL',   '부장', 60, 60)
ON DUPLICATE KEY UPDATE
    position_name = VALUES(position_name),
    position_level = VALUES(position_level),
    sort_order = VALUES(sort_order),
    active = TRUE,
    deleted_at = NULL;

INSERT INTO roles (role_code, role_name, description, system_role)
VALUES
    ('ROLE_ADMIN',    '관리자', '모든 업무 및 시스템 관리 권한', TRUE),
    ('ROLE_MANAGER',  '팀장',   '팀원 조회, 휴가 및 결재 승인 권한', TRUE),
    ('ROLE_EMPLOYEE', '사원',   '본인 정보와 일반 업무 기능', TRUE)
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    description = VALUES(description),
    system_role = VALUES(system_role),
    active = TRUE;

INSERT INTO permissions
    (permission_code, permission_name, resource_name, action_name)
VALUES
    ('DASHBOARD:READ',       '대시보드 조회',       'DASHBOARD',    'READ'),
    ('ACCOUNT:MANAGE',       '회원 관리',           'ACCOUNT',      'MANAGE'),
    ('EMPLOYEE:READ_SELF',   '내 정보 조회',        'EMPLOYEE',     'READ_SELF'),
    ('EMPLOYEE:READ_TEAM',   '팀원 조회',           'EMPLOYEE',     'READ_TEAM'),
    ('EMPLOYEE:READ_ALL',    '전체 사원 조회',      'EMPLOYEE',     'READ_ALL'),
    ('EMPLOYEE:CREATE',      '사원 등록',           'EMPLOYEE',     'CREATE'),
    ('EMPLOYEE:UPDATE',      '사원 수정',           'EMPLOYEE',     'UPDATE'),
    ('EMPLOYEE:DELETE',      '사원 삭제',           'EMPLOYEE',     'DELETE'),
    ('EMPLOYEE:IMPORT',      '사원 엑셀 업로드',    'EMPLOYEE',     'IMPORT'),
    ('EMPLOYEE:EXPORT',      '사원 엑셀 다운로드',  'EMPLOYEE',     'EXPORT'),
    ('ORGANIZATION:READ',    '조직 조회',           'ORGANIZATION', 'READ'),
    ('ORGANIZATION:MANAGE',  '조직 관리',           'ORGANIZATION', 'MANAGE'),
    ('LEAVE:READ_SELF',      '내 휴가 조회',        'LEAVE',        'READ_SELF'),
    ('LEAVE:REQUEST',        '휴가 신청',           'LEAVE',        'REQUEST'),
    ('LEAVE:READ_TEAM',      '팀 휴가 조회',        'LEAVE',        'READ_TEAM'),
    ('LEAVE:READ_ALL',       '전체 휴가 조회',      'LEAVE',        'READ_ALL'),
    ('LEAVE:APPROVE',        '휴가 승인',           'LEAVE',        'APPROVE'),
    ('LEAVE:MANAGE',         '휴가 정책 관리',      'LEAVE',        'MANAGE'),
    ('LEAVE:EXPORT',         '휴가 엑셀 다운로드',  'LEAVE',        'EXPORT'),
    ('APPROVAL:READ_SELF',   '내 결재문서 조회',    'APPROVAL',     'READ_SELF'),
    ('APPROVAL:REQUEST',     '결재 요청',           'APPROVAL',     'REQUEST'),
    ('APPROVAL:APPROVE',     '결재 처리',           'APPROVAL',     'APPROVE'),
    ('APPROVAL:READ_ALL',    '전체 결재 조회',      'APPROVAL',     'READ_ALL'),
    ('APPROVAL:MANAGE',      '결재 양식 관리',      'APPROVAL',     'MANAGE'),
    ('ATTENDANCE:READ_SELF', '내 근태 조회',        'ATTENDANCE',   'READ_SELF'),
    ('ATTENDANCE:CLOCK',     '출퇴근 기록',         'ATTENDANCE',   'CLOCK'),
    ('ATTENDANCE:READ_TEAM', '팀 근태 조회',        'ATTENDANCE',   'READ_TEAM'),
    ('ATTENDANCE:MANAGE',    '근태 관리',           'ATTENDANCE',   'MANAGE'),
    ('NOTICE:READ',          '공지 조회',           'NOTICE',       'READ'),
    ('NOTICE:MANAGE',        '공지 관리',           'NOTICE',       'MANAGE'),
    ('NOTICE:EXPORT',        '공지 엑셀 다운로드',  'NOTICE',       'EXPORT'),
    ('RESOURCE:READ',        '자료실 조회',         'RESOURCE',     'READ'),
    ('RESOURCE:MANAGE',      '자료실 관리',         'RESOURCE',     'MANAGE'),
    ('SCHEDULE:READ',        '일정 조회',           'SCHEDULE',     'READ'),
    ('SCHEDULE:MANAGE',      '일정 관리',           'SCHEDULE',     'MANAGE'),
    ('MESSAGE:USE',          '사내 쪽지 사용',      'MESSAGE',      'USE'),
    ('NOTIFICATION:READ',    '알림 조회',           'NOTIFICATION', 'READ'),
    ('AUDIT:READ',           '감사 로그 조회',      'AUDIT',        'READ'),
    ('SYSTEM:MANAGE',        '시스템 설정 관리',    'SYSTEM',       'MANAGE'),
    ('SYSTEM:MONITOR',       '시스템 모니터링',     'SYSTEM',       'MONITOR')
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    resource_name = VALUES(resource_name),
    action_name = VALUES(action_name);

-- Administrator receives all current permissions. Re-run this statement after adding
-- a permission, or perform the equivalent in a migration.
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
CROSS JOIN permissions p
WHERE r.role_code = 'ROLE_ADMIN';

-- Team leader permissions. Ownership/scope checks must still be enforced in the
-- service layer so a leader can only access employees in their own organization tree.
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.permission_code IN (
    'DASHBOARD:READ', 'EMPLOYEE:READ_SELF', 'EMPLOYEE:READ_TEAM',
    'ORGANIZATION:READ', 'LEAVE:READ_SELF', 'LEAVE:REQUEST',
    'LEAVE:READ_TEAM', 'LEAVE:APPROVE', 'APPROVAL:READ_SELF',
    'APPROVAL:REQUEST', 'APPROVAL:APPROVE', 'ATTENDANCE:READ_SELF',
    'ATTENDANCE:CLOCK', 'ATTENDANCE:READ_TEAM', 'NOTICE:READ',
    'RESOURCE:READ', 'SCHEDULE:READ', 'SCHEDULE:MANAGE',
    'MESSAGE:USE', 'NOTIFICATION:READ'
)
WHERE r.role_code = 'ROLE_MANAGER';

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.permission_code IN (
    'DASHBOARD:READ', 'EMPLOYEE:READ_SELF', 'ORGANIZATION:READ',
    'LEAVE:READ_SELF', 'LEAVE:REQUEST', 'APPROVAL:READ_SELF',
    'APPROVAL:REQUEST', 'ATTENDANCE:READ_SELF',
    'ATTENDANCE:CLOCK', 'NOTICE:READ', 'RESOURCE:READ',
    'SCHEDULE:READ', 'SCHEDULE:MANAGE', 'MESSAGE:USE', 'NOTIFICATION:READ'
)
WHERE r.role_code = 'ROLE_EMPLOYEE';

INSERT INTO menus (menu_code, menu_name, route_path, icon_name, sort_order)
VALUES
    ('DASHBOARD',    '대시보드',   '/dashboard',    'dashboard',     10),
    ('MY_PAGE',      '마이페이지', '/my-page',      'person',        20),
    ('EMPLOYEE',     '사원관리',   '/employees',    'groups',        30),
    ('ORGANIZATION', '조직관리',   '/organization', 'account_tree',  40),
    ('LEAVE',        '연차관리',   '/leaves',       'beach_access',  50),
    ('APPROVAL',     '전자결재',   '/approvals',    'approval',      60),
    ('ATTENDANCE',   '근태관리',   '/attendance',   'schedule',      70),
    ('NOTICE',       '공지사항',   '/notices',      'campaign',      80),
    ('RESOURCE',     '자료실',     '/resources',    'folder',        90),
    ('CALENDAR',     '일정관리',   '/calendar',     'calendar_month',100),
    ('MESSAGE',      '메신저',     '/messages',     'mail',         110),
    ('AUDIT',        '감사로그',   '/admin/audits', 'history',      120),
    ('SYSTEM',       '시스템설정', '/admin/settings','settings',    130),
    ('MONITORING',   '모니터링',   '/admin/monitoring','monitor_heart',140)
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    route_path = VALUES(route_path),
    icon_name = VALUES(icon_name),
    sort_order = VALUES(sort_order),
    active = TRUE;

INSERT IGNORE INTO menu_permissions (menu_id, permission_id)
SELECT m.menu_id, p.permission_id
FROM menus m
JOIN permissions p ON
       (m.menu_code = 'DASHBOARD'    AND p.permission_code = 'DASHBOARD:READ')
    OR (m.menu_code = 'MY_PAGE'      AND p.permission_code = 'EMPLOYEE:READ_SELF')
    OR (m.menu_code = 'EMPLOYEE'     AND p.permission_code IN
                                             ('EMPLOYEE:READ_TEAM', 'EMPLOYEE:READ_ALL'))
    OR (m.menu_code = 'ORGANIZATION' AND p.permission_code = 'ORGANIZATION:READ')
    OR (m.menu_code = 'LEAVE'        AND p.permission_code = 'LEAVE:READ_SELF')
    OR (m.menu_code = 'APPROVAL'     AND p.permission_code = 'APPROVAL:READ_SELF')
    OR (m.menu_code = 'ATTENDANCE'   AND p.permission_code = 'ATTENDANCE:READ_SELF')
    OR (m.menu_code = 'NOTICE'       AND p.permission_code = 'NOTICE:READ')
    OR (m.menu_code = 'RESOURCE'     AND p.permission_code = 'RESOURCE:READ')
    OR (m.menu_code = 'CALENDAR'     AND p.permission_code = 'SCHEDULE:READ')
    OR (m.menu_code = 'MESSAGE'      AND p.permission_code = 'MESSAGE:USE')
    OR (m.menu_code = 'AUDIT'        AND p.permission_code = 'AUDIT:READ')
    OR (m.menu_code = 'SYSTEM'       AND p.permission_code = 'SYSTEM:MANAGE')
    OR (m.menu_code = 'MONITORING'   AND p.permission_code = 'SYSTEM:MONITOR');

INSERT INTO leave_policies
    (company_id, policy_name, effective_from, base_annual_days,
     first_year_monthly_days, max_first_year_days, max_carry_over_days)
VALUES
    (@company_id, '기본 연차 정책', '2026-01-01', 15.00, 1.00, 11.00, 0.00)
ON DUPLICATE KEY UPDATE
    base_annual_days = VALUES(base_annual_days),
    first_year_monthly_days = VALUES(first_year_monthly_days),
    max_first_year_days = VALUES(max_first_year_days),
    max_carry_over_days = VALUES(max_carry_over_days),
    active = TRUE;

INSERT INTO leave_types
    (company_id, leave_type_code, leave_type_name, deducts_annual_leave,
     requires_reason, requires_attachment, sort_order)
VALUES
    (@company_id, 'ANNUAL',      '연차',     TRUE,  TRUE,  FALSE, 10),
    (@company_id, 'HALF_DAY',    '반차',     TRUE,  TRUE,  FALSE, 20),
    (@company_id, 'SICK',        '병가',     FALSE, TRUE,  TRUE,  30),
    (@company_id, 'OFFICIAL',    '공가',     FALSE, TRUE,  TRUE,  40),
    (@company_id, 'BEREAVEMENT', '경조휴가', FALSE, TRUE,  TRUE,  50)
ON DUPLICATE KEY UPDATE
    leave_type_name = VALUES(leave_type_name),
    deducts_annual_leave = VALUES(deducts_annual_leave),
    requires_reason = VALUES(requires_reason),
    requires_attachment = VALUES(requires_attachment),
    sort_order = VALUES(sort_order),
    active = TRUE;

INSERT INTO work_policies
    (company_id, policy_name, work_start_time, work_end_time,
     break_minutes, standard_work_minutes, effective_from)
VALUES
    (@company_id, '기본 근무제', '09:00:00', '18:00:00', 60, 480, '2026-01-01')
ON DUPLICATE KEY UPDATE
    work_start_time = VALUES(work_start_time),
    work_end_time = VALUES(work_end_time),
    break_minutes = VALUES(break_minutes),
    standard_work_minutes = VALUES(standard_work_minutes),
    active = TRUE;

INSERT INTO notice_categories (company_id, category_name, sort_order)
VALUES
    (@company_id, '일반', 10),
    (@company_id, '인사', 20),
    (@company_id, '시스템', 30)
ON DUPLICATE KEY UPDATE
    sort_order = VALUES(sort_order),
    active = TRUE;

INSERT INTO password_policies
    (company_id, min_length, max_length, require_uppercase, require_lowercase,
     require_digit, require_special, expiry_days, history_count,
     max_failed_attempts, lock_minutes, reset_token_minutes, session_timeout_minutes)
VALUES
    (@company_id, 8, 100, TRUE, TRUE, TRUE, TRUE, 90, 5, 5, 30, 30, 30)
ON DUPLICATE KEY UPDATE
    min_length = VALUES(min_length),
    max_length = VALUES(max_length),
    require_uppercase = VALUES(require_uppercase),
    require_lowercase = VALUES(require_lowercase),
    require_digit = VALUES(require_digit),
    require_special = VALUES(require_special),
    expiry_days = VALUES(expiry_days),
    history_count = VALUES(history_count),
    max_failed_attempts = VALUES(max_failed_attempts),
    lock_minutes = VALUES(lock_minutes),
    reset_token_minutes = VALUES(reset_token_minutes),
    session_timeout_minutes = VALUES(session_timeout_minutes);

-- End of schema.
