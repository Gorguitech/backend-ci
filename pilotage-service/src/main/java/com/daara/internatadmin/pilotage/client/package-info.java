/**
 * Clients des endpoints internes des autres services.
 * <p>
 * Chaque champ des vues est obligatoire ({@code @JsonProperty(required = true)}) : une reponse incomplete
 * (champ renomme ou absent) fait echouer le decodage, donc la requete en 503, au lieu de donner un zero.
 */
package com.daara.internatadmin.pilotage.client;
