package ht.dinasa.fact.securite.infrastructure;

import ht.dinasa.fact.securite.application.DepotPermissions;
import ht.dinasa.fact.securite.domain.Permission;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
class DepotPermissionsSql implements DepotPermissions {

    private final JdbcClient jdbc;

    DepotPermissionsSql(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<Permission> duGroupe(long idGroupe) {
        Set<Permission> p = EnumSet.noneOf(Permission.class);
        jdbc.sql("SELECT PERMISSION FROM APP.GROUPE_PERMISSION WHERE GROUP_ID = :g")
                .param("g", idGroupe).query(String.class).list()
                .forEach(code -> Permission.depuis(code).ifPresent(p::add));
        return p;
    }

    @Override
    public List<GroupeVue> groupes() {
        Map<Long, Set<Permission>> parGroupe = new HashMap<>();
        jdbc.sql("SELECT GROUP_ID, PERMISSION FROM APP.GROUPE_PERMISSION")
                .query((rs, n) -> {
                    long idGroupe = rs.getLong("GROUP_ID");          // lu AVANT le bloc
                    String code = rs.getString("PERMISSION");
                    Permission.depuis(code).ifPresent(p ->
                            parGroupe.computeIfAbsent(idGroupe, k -> EnumSet.noneOf(Permission.class)).add(p));
                    return idGroupe;
                }).list();
        return jdbc.sql("""
                        SELECT g.Group_ID, RTRIM(g.GroupDesc) AS LIBELLE,
                               (SELECT COUNT(*) FROM dbo.tbl_S_User u WHERE u.Group_ID = g.Group_ID AND u.ActifYN = 1) AS NB
                          FROM dbo.tbl_S_Group g
                         ORDER BY g.GroupDesc""")
                .query((rs, n) -> new GroupeVue(rs.getLong("Group_ID"), rs.getString("LIBELLE"), rs.getInt("NB"),
                        parGroupe.getOrDefault(rs.getLong("Group_ID"), EnumSet.noneOf(Permission.class))))
                .list();
    }

    @Override
    public boolean groupeExiste(long idGroupe) {
        return jdbc.sql("SELECT COUNT(*) FROM dbo.tbl_S_Group WHERE Group_ID = :g")
                .param("g", idGroupe).query(Integer.class).single() > 0;
    }

    @Override
    public void remplacer(long idGroupe, Set<Permission> permissions, LocalDateTime le, String par) {
        jdbc.sql("DELETE FROM APP.GROUPE_PERMISSION WHERE GROUP_ID = :g").param("g", idGroupe).update();
        for (Permission p : permissions) {
            jdbc.sql("""
                    INSERT INTO APP.GROUPE_PERMISSION (GROUP_ID, PERMISSION, ACCORDE_LE, ACCORDE_PAR)
                    VALUES (:g, :p, :le, :par)""")
                    .param("g", idGroupe).param("p", p.name()).param("le", le).param("par", par).update();
        }
    }

    @Override
    public int groupesAvec(Permission permission) {
        return jdbc.sql("SELECT COUNT(DISTINCT GROUP_ID) FROM APP.GROUPE_PERMISSION WHERE PERMISSION = :p")
                .param("p", permission.name()).query(Integer.class).single();
    }

    @Override
    public boolean aucune() {
        return jdbc.sql("SELECT COUNT(*) FROM APP.GROUPE_PERMISSION").query(Integer.class).single() == 0;
    }
}