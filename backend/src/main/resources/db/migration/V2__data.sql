-- SUPER ADMIN
INSERT INTO Nutzer (id, email, password_hash, role, first_name, last_name, is_active, version)
VALUES (NEXT VALUE FOR id_sequence, 'juergenkrey@yahoo.de', '$2a$10$8920ztppz0N29GNOOw1FCuZJqMUIJhybpqw9tKwkS0rR3BmELJIlC',
        'ADMIN', 'Jürgen', 'Krey', 1, 1);
