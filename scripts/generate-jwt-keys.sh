#!/usr/bin/env bash
#
# Genere une paire de cles RSA 2048 bits pour signer les JWT (RS256).
#
# - La cle privee (PKCS#8) n'est utilisee que par auth-service.
# - La cle publique (X.509 SubjectPublicKeyInfo) est distribuee a la gateway et aux services.
#
# Les deux cles sont affichees au format PEM encode en base64 sur une seule ligne,
# pret a etre colle dans le fichier .env. Aucun fichier de cle n'est conserve sur le disque.
#
# Usage : ./scripts/generate-jwt-keys.sh >> .env
#         (ou copier manuellement les deux lignes affichees)

set -euo pipefail

if ! command -v openssl >/dev/null 2>&1; then
    echo "openssl est requis" >&2
    exit 1
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$workdir/private.pem" 2>/dev/null
openssl pkey -in "$workdir/private.pem" -pubout -out "$workdir/public.pem"

# base64 sans retour a la ligne, compatible macOS et Linux
encode() { base64 < "$1" | tr -d '\n'; }

echo "JWT_PRIVATE_KEY_B64=$(encode "$workdir/private.pem")"
echo "JWT_PUBLIC_KEY_B64=$(encode "$workdir/public.pem")"
