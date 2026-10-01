package com.enterpriselab.api.workorders;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ayudante de las IT de la spec 04: no hay API de alta de usuarios, así que el segundo
 * técnico de guardia (para los {@code 409} por dueño ajeno y las carreras) se crea por
 * SQL, con el hash de la clave de {@code tecnico} (misma clave: {@code tecnico123}), y se
 * borra al terminar. Sus órdenes tienen que haberse limpiado antes: la FK de
 * {@code work_orders} hacia {@code users} impide borrarlo con órdenes a su nombre.
 */
public final class SecondGuardiaTechnician {

    public static final String USERNAME = "tecnico.dos";
    public static final String PASSWORD = "tecnico123";
    public static final String DISPLAY_NAME = "Técnico Dos de Guardia";
    private static final String LEGAJO = "7777001";

    private SecondGuardiaTechnician() {
    }

    /** Crea el técnico y devuelve el id de su usuario. */
    public static long create(JdbcTemplate jdbc) {
        delete(jdbc);
        jdbc.update("insert into technicians (legajo, first_name, last_name, specialty, team_type) "
                + "values (?, 'Dos', 'Guardia', 'mecanico', 'guardia')", LEGAJO);
        jdbc.update("insert into users (username, password_hash, display_name, email, role, technician_id) "
                + "select ?, u.password_hash, ?, 'tecnico.dos@enterprise-lab.dev', 'tecnico', t.id "
                + "from users u, technicians t where u.username = 'tecnico' and t.legajo = ?",
                USERNAME, DISPLAY_NAME, LEGAJO);
        return jdbc.queryForObject("select id from users where username = ?", Long.class, USERNAME);
    }

    public static void delete(JdbcTemplate jdbc) {
        jdbc.update("delete from users where username = ?", USERNAME);
        jdbc.update("delete from technicians where legajo = ?", LEGAJO);
    }
}
