package ht.dinasa.fact.securite.application;

import ht.dinasa.fact.securite.domain.Permission;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface DepotPermissions {

    record GroupeVue(long id, String libelle, int utilisateursActifs, Set<Permission> permissions) {
    }

    Set<Permission> duGroupe(long idGroupe);

    List<GroupeVue> groupes();

    boolean groupeExiste(long idGroupe);

    void remplacer(long idGroupe, Set<Permission> permissions, LocalDateTime le, String par);

    int groupesAvec(Permission permission);

    boolean aucune();
}