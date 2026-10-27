package com.daara.internatadmin.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Trace l'action dans l'audit apres son succes. Le detail enregistre est le toString() de la valeur retournee :
 * ne jamais annoter une methode dont le retour contient un mot de passe, un code de recuperation ou un jeton.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {
    String action();

    String entityType();
}
