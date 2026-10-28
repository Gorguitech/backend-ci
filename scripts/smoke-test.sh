#!/usr/bin/env bash
#
# Test de fumee du backend deploye (profil Compose all), uniquement par la gateway, comme le frontend.
#
# Parcours : sante de la gateway ; login admin ; creation d'un gestionnaire ; changement obligatoire de son
# mot de passe (403 MUST_CHANGE_PASSWORD avant) ; creation d'un eleve ; paiement partiel avec ses deux 409
# (PARTIAL_PAYMENT_NOT_CONFIRMED puis AMOUNT_MISMATCH) ; ligne d'audit du paiement ; tableau de bord ;
# desactivation du gestionnaire et 403 immediat avec son jeton encore valide.
#
# Usage : SMOKE_ADMIN_PASSWORD=... ./scripts/smoke-test.sh
#   SMOKE_ADMIN_PASSWORD : mot de passe actuel du compte admin (deja change apres la premiere connexion)
#   GATEWAY_URL          : http://localhost:8080 par defaut
# Les mots de passe restent en memoire : rien n'est ecrit sur le disque. Code de sortie 0 si tout passe.

set -uo pipefail

G="${GATEWAY_URL:-http://localhost:8080}"
J='Content-Type: application/json'
if [[ -z "${SMOKE_ADMIN_PASSWORD:-}" ]]; then
    echo "SMOKE_ADMIN_PASSWORD est obligatoire" >&2
    exit 2
fi

failures=0
BODY=""
check() { # libelle, attendu, obtenu
    if [[ "$3" == "$2" ]]; then
        echo "  ok  $1 ($3)"
    else
        echo "  KO  $1 : attendu $2, obtenu $3 $BODY"
        failures=$((failures + 1))
    fi
}
# requete : methode chemin jeton [corps] ; statut dans CODE, corps dans BODY
req() {
    local out
    out="$(curl -s -w '\n%{http_code}' -X "$1" -H "$J" ${3:+-H "Authorization: Bearer $3"} ${4:+-d "$4"} "$G$2")"
    CODE="${out##*$'\n'}"
    BODY="${out%$'\n'*}"
}
field() { python3 -c "import json,sys; print(json.loads(sys.argv[1])$1)" "$BODY"; }
login() { req POST /api/auth/login "" "$(python3 -c 'import json,sys; print(json.dumps({"username": sys.argv[1], "password": sys.argv[2]}))' "$1" "$2")"; }

suffix="$(LC_ALL=C tr -dc 'a-z' </dev/urandom | dd bs=6 count=1 2>/dev/null)"
phone="77$(LC_ALL=C tr -dc '0-9' </dev/urandom | dd bs=7 count=1 2>/dev/null)"
month="$(date +%m | sed 's/^0//')"
year="$(date +%Y)"
today="$(date +%F)"

echo "Gateway $G"
req GET /health ""
check "sante de la gateway" 200 "$CODE"

login admin "$SMOKE_ADMIN_PASSWORD"
check "login admin" 200 "$CODE"
[[ "$CODE" == 200 ]] || { echo "Arret : login admin impossible"; exit 1; }
if [[ "$(field "['mustChangePassword']")" == "True" ]]; then
    echo "Arret : changer d'abord le mot de passe initial de l'admin"; exit 1
fi
ADMIN="$(field "['token']")"

req POST /api/users "$ADMIN" "{\"firstName\":\"Smoke\",\"lastName\":\"Test$suffix\"}"
check "creation du gestionnaire" 201 "$CODE"
username="$(field "['username']")"
temporary="$(field "['temporaryPassword']")"
login "$username" "$temporary"
check "login avec le mot de passe temporaire" 200 "$CODE"
TEMP_TOKEN="$(field "['token']")"
req GET /api/enfants "$TEMP_TOKEN"
check "acces bloque avant changement du mot de passe" "403 MUST_CHANGE_PASSWORD" "$CODE $(field ".get('error')" 2>/dev/null)"
new_password="Smoke-$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | dd bs=16 count=1 2>/dev/null)"
req PUT /api/users/me/password "$TEMP_TOKEN" \
    "$(python3 -c 'import json,sys; print(json.dumps({"currentPassword": sys.argv[1], "newPassword": sys.argv[2]}))' "$temporary" "$new_password")"
check "changement du mot de passe" 200 "$CODE"
login "$username" "$new_password"
check "login avec le nouveau mot de passe" 200 "$CODE"
GEST="$(field "['token']")"
unset temporary new_password TEMP_TOKEN

req POST '/api/enfants?confirmDuplicate=false' "$GEST" "{\"firstName\":\"Fumee\",\"lastName\":\"Essai$suffix\",\"approximateAge\":8,\"gender\":\"M\",\"guardianName\":\"Tuteur Essai\",\"guardianPhone\":\"$phone\",\"entryDate\":\"$today\",\"registrationFee\":15000,\"monthlyFee\":10000}"
check "creation de l'eleve" 201 "$CODE"
enfant="$(field "['id']")"

payment() { echo "{\"enfantId\":$enfant,\"paymentType\":\"MENSUALITE\",\"paymentMethod\":\"ESPECES\",\"amount\":$1,\"concernedYear\":$year,\"concernedMonth\":$month,\"paymentDate\":\"$today\",\"confirmPartial\":$2}"; }
req POST /api/paiements-enfants "$GEST" "$(payment 4000 false)"
check "paiement partiel non confirme" "409 PARTIAL_PAYMENT_NOT_CONFIRMED" "$CODE $(field ".get('error')" 2>/dev/null)"
req POST /api/paiements-enfants "$GEST" "$(payment 20000 true)"
check "montant superieur au du" "409 AMOUNT_MISMATCH" "$CODE $(field ".get('error')" 2>/dev/null)"
req POST /api/paiements-enfants "$GEST" "$(payment 4000 true)"
check "paiement partiel confirme" 201 "$CODE"
paiement="$(field "['id']")"

# publication de l'audit asynchrone, apres le commit
audit=0
for _ in $(seq 1 20); do
    req GET "/api/audit?entityType=PaiementEnfant&entityId=$paiement" "$ADMIN"
    audit="$(field "['totalElements']" 2>/dev/null || echo 0)"
    [[ "$audit" -ge 1 ]] 2>/dev/null && break
    sleep 0.5
done
check "ligne d'audit du paiement" 1 "$audit"

req GET /api/dashboard "$GEST"
check "tableau de bord" 200 "$CODE"

req GET /api/users "$ADMIN"
user_id="$(python3 -c 'import json,sys; print([u["id"] for u in json.loads(sys.argv[1]) if u["username"] == sys.argv[2]][0])' "$BODY" "$username")"
req POST "/api/users/$user_id/deactivate" "$ADMIN"
check "desactivation du gestionnaire" 200 "$CODE"
req GET /api/enfants "$GEST"
check "jeton encore valide d'un compte desactive" 403 "$CODE"
unset GEST ADMIN

if [[ $failures -eq 0 ]]; then
    echo "RESULTAT : test de fumee reussi"
else
    echo "RESULTAT : $failures echec(s)"
fi
exit $((failures > 0))
