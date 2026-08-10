-- Demo accounts for local use. Each key_hash below is sha256(<raw key>):
--   alice-demo-key  ->  0572c17e...  (USER)
--   bob-demo-key    ->  3a1f6bae...  (USER)
--   carol-demo-key  ->  05e17cac...  (USER)
--   admin-demo-key  ->  806647bb...  (ADMIN)

INSERT INTO users (email, display_name)
VALUES ('alice@example.com', 'Alice'),
       ('bob@example.com', 'Bob'),
       ('carol@example.com', 'Carol'),
       ('admin@example.com', 'Admin');

INSERT INTO api_key (user_id, key_hash, role)
SELECT id, '0572c17ed012b3efdf9df98db1718f225887132739b8da945d81ac5a7d1fea45', 'USER'
FROM users
WHERE email = 'alice@example.com';

INSERT INTO api_key (user_id, key_hash, role)
SELECT id, '3a1f6bae21de4f036f2aba80fce463677f1070f8bf81f0f475604cccd8e2d7f3', 'USER'
FROM users
WHERE email = 'bob@example.com';

INSERT INTO api_key (user_id, key_hash, role)
SELECT id, '05e17cacfd02d6850325b37f5cec8ca718ece990dae91990f6917effb1dd6b16', 'USER'
FROM users
WHERE email = 'carol@example.com';

INSERT INTO api_key (user_id, key_hash, role)
SELECT id, '806647bb62d32253b6797dad9d71d211d172005a6564317a920e9ba78322a338', 'ADMIN'
FROM users
WHERE email = 'admin@example.com';
