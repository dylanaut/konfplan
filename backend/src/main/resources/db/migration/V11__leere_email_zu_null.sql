-- AdminService.createUser() speicherte bei leerem E-Mail-Feld einen Leerstring statt null.
-- NULL ist vom UNIQUE-Constraint auf email ausgenommen, ein Leerstring nicht - der zweite
-- Nutzer ohne E-Mail-Adresse scheiterte daher an "duplicate key value" (siehe #282).
-- Bereits so entstandene Leerstrings werden hier auf NULL normalisiert.
update Nutzer
set email = null
where email = '';
