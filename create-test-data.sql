-- create-test-data.sql

-- ลบข้อมูลเก่า (ถ้ามี)
TRUNCATE TABLE user_roles CASCADE;
TRUNCATE TABLE users CASCADE;
TRUNCATE TABLE roles CASCADE;

-- สร้าง Roles
INSERT INTO roles (id, name) VALUES 
(1, 'USER'),
(2, 'ADMIN'),
(3, 'MANAGER');

-- reset sequence
SELECT setval('roles_id_seq', (SELECT MAX(id) FROM roles));

-- สร้าง Users (password: password123)
-- password เข้ารหัสด้วย BCrypt: $2a$10$NkM3J3M3J3M3J3M3J3M3Ju
INSERT INTO users (id, username, password, email, full_name, created_at) VALUES 
(1, 'john_doe', '$2a$10$NkM3J3M3J3M3J3M3J3M3Ju', 'john@example.com', 'John Doe', NOW()),
(2, 'jane_smith', '$2a$10$NkM3J3M3J3M3J3M3J3M3Ju', 'jane@example.com', 'Jane Smith', NOW()),
(3, 'admin_user', '$2a$10$NkM3J3M3J3M3J3M3J3M3Ju', 'admin@example.com', 'Admin User', NOW()),
(4, 'manager_user', '$2a$10$NkM3J3M3J3M3J3M3J3M3Ju', 'manager@example.com', 'Manager User', NOW()),
(5, 'test_user', '$2a$10$NkM3J3M3J3M3J3M3J3M3Ju', 'test@example.com', 'Test User', NOW());

-- reset sequence
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));

-- Assign Roles
-- john_doe -> USER
INSERT INTO user_roles (user_id, role_id) VALUES (1, 1);

-- jane_smith -> USER, MANAGER
INSERT INTO user_roles (user_id, role_id) VALUES (2, 1);
INSERT INTO user_roles (user_id, role_id) VALUES (2, 3);

-- admin_user -> USER, ADMIN
INSERT INTO user_roles (user_id, role_id) VALUES (3, 1);
INSERT INTO user_roles (user_id, role_id) VALUES (3, 2);

-- manager_user -> USER, MANAGER
INSERT INTO user_roles (user_id, role_id) VALUES (4, 1);
INSERT INTO user_roles (user_id, role_id) VALUES (4, 3);

-- test_user -> USER
INSERT INTO user_roles (user_id, role_id) VALUES (5, 1);

-- ตรวจสอบข้อมูล
SELECT u.id, u.username, u.email, u.full_name, r.name as role
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
ORDER BY u.id;