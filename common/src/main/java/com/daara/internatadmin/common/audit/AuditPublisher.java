package com.daara.internatadmin.common.audit;

/** Envoi d'un evenement d'audit. Ne doit jamais faire echouer l'action metier. */
public interface AuditPublisher {
    void publish(AuditEvent event);
}
