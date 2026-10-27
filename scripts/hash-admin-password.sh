#!/usr/bin/env bash
#
# Calcule le hash BCrypt du mot de passe initial du compte admin et affiche la ligne a placer dans .env.
#
# Le hash contient des caracteres $ : il est ecrit entre apostrophes simples pour que ni Docker Compose
# ni le shell (set -a; . ./.env) ne les interpretent. Ne jamais l'ecrire sans apostrophes.
#
# Usage : ./scripts/hash-admin-password.sh            (le mot de passe est demande, sans echo)
#         ADMIN_PASSWORD=... ./scripts/hash-admin-password.sh   (usage non interactif, scripts de test)
#
# Le mot de passe en clair n'est jamais ecrit sur le disque. Le changement est force a la premiere connexion.

set -euo pipefail

password="${ADMIN_PASSWORD:-}"
if [[ -z "$password" ]]; then
    read -r -s -p "Mot de passe initial de l'admin : " password
    echo >&2
fi
if [[ ${#password} -lt 8 ]]; then
    echo "Le mot de passe doit contenir au moins 8 caracteres" >&2
    exit 1
fi

if command -v htpasswd >/dev/null 2>&1; then
    hash="$(htpasswd -inBC 12 "" <<<"$password" | tr -d ':\n')"
elif command -v docker >/dev/null 2>&1; then
    hash="$(docker run --rm -i httpd:2.4-alpine htpasswd -inBC 12 "" <<<"$password" | tr -d ':\n')"
else
    echo "htpasswd ou docker est requis" >&2
    exit 1
fi

if [[ ! "$hash" =~ ^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$ ]]; then
    echo "Hash BCrypt inattendu" >&2
    exit 1
fi

echo "ADMIN_INITIAL_PASSWORD_HASH='$hash'"
