-- =========================================================================
-- CHẠY SCRIPT NÀY TRÊN DATABASE: assignment_db (SAU KHI course-service CHẠY XONG)
-- =========================================================================

-- 1. Tạo Lớp học (Class)
INSERT INTO classes (id, owner_id, name, semester, status)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111', -- UUID của lecturer_test
    'Lập trình Mạng',
    '2026-HK1',
    'ACTIVE'
) ON CONFLICT (id) DO NOTHING;

-- 2. Thêm Sinh viên vào lớp
INSERT INTO class_students (id, class_id, student_user_id, student_code, student_name, email)
VALUES (
    '44444444-4444-4444-4444-444444444444',
    '33333333-3333-3333-3333-333333333333',
    '22222222-2222-2222-2222-222222222222', -- UUID của student_test từ Keycloak
    'B20DCCN001',
    'Student Test',
    'student@ptit.edu.vn'
) ON CONFLICT (id) DO UPDATE SET student_user_id = EXCLUDED.student_user_id;

-- 3. Tạo Bài tập (Assignment)
INSERT INTO assignments (id, class_id, owner_id, title, description, grading_strategy, docker_compose_port, published)
VALUES (
    '55555555-5555-5555-5555-555555555555',
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    'Bài tập 1: HTTP Server',
    'Viết HTTP server trả về JSON',
    'STUDENT_DOCKER_COMPOSE',
    8080,
    true
) ON CONFLICT (id) DO NOTHING;

-- 4. Tạo Test Plan
INSERT INTO test_plans (id, assignment_id, name, description, sequence_order, weight)
VALUES (
    '66666666-6666-6666-6666-666666666666',
    '55555555-5555-5555-5555-555555555555',
    'Test Cơ bản',
    'Kiểm tra endpoint /ping',
    1,
    10
) ON CONFLICT (id) DO NOTHING;

-- 5. Tạo Test Step (Ví dụ: Gọi API HTTP)
INSERT INTO test_steps (id, plan_id, step_order, name, step_type, config, expected_result, weight, is_required)
VALUES (
    '77777777-7777-7777-7777-777777777777',
    '66666666-6666-6666-6666-666666666666',
    1,
    'Ping Test',
    'HTTP_REQUEST',
    '{"method": "GET", "path": "/ping"}',
    '{"status": 200, "body_contains": "pong"}',
    10,
    true
) ON CONFLICT (id) DO NOTHING;
