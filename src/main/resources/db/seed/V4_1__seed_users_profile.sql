-- Enmienda 00-A — nombre visible y correo de los usuarios de desarrollo
-- (REQ-16). Solo se carga en el perfil "dev" (application.yml suma db/seed a
-- spring.flyway.locations ahí y solo ahí): nunca debe llegar a un entorno
-- productivo.
--
-- Corre entre V4 (columnas nulables) y V5 (columnas obligatorias). Los valores
-- son los de db.json del frontend. Se COMPLETAN con UPDATE los usuarios que ya
-- sembró V1_1, así conservan su id, su contraseña y su vínculo con el técnico.
-- Si faltara alguno, V5 no falla (no quedaría fila con NULL) pero el usuario no
-- existiría: SeedUsersIT lo detecta.

UPDATE users SET display_name = 'Administrador',
                 email = 'admin@enterprise-lab.dev'
 WHERE username = 'admin';

UPDATE users SET display_name = 'Team Leader de Mantenimiento',
                 email = 'teamleader@enterprise-lab.dev'
 WHERE username = 'teamleader';

UPDATE users SET display_name = 'Personal de Producción',
                 email = 'produccion@enterprise-lab.dev'
 WHERE username = 'produccion';

UPDATE users SET display_name = 'Técnico Mecánico de Guardia',
                 email = 'tecnico@enterprise-lab.dev'
 WHERE username = 'tecnico';

UPDATE users SET display_name = 'Técnico Electricista Preventivo',
                 email = 'electricista@enterprise-lab.dev'
 WHERE username = 'electricista';
