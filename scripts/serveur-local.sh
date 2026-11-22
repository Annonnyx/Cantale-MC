#!/usr/bin/env bash
# Serveur Paper local. Ce terminal est la console : Ctrl+C arrête le processus.
# L'API v2 (https://api.papermc.io/v2/...) répond HTTP 410 depuis le 2026-07-01.
# Le build stable est pris sur l'API officielle Fill v3.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="${ROOT}/run/server"
MC_VERSION="1.21.11"
API_BUILDS="https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds"
USER_AGENT="Cantale-serveur-local/1.0"

die() {
  printf '%s\n' "$@" >&2
  exit 1
}

require_java21() {
  if ! command -v java >/dev/null 2>&1; then
    die \
      "Java 21 est introuvable dans le PATH." \
      "Installez-le, puis relancez ce script :" \
      "sudo pacman -S --needed jdk21-openjdk"
  fi

  local java_version_line
  java_version_line="$(java -version 2>&1 || true)"
  java_version_line="${java_version_line%%$'\n'*}"
  if [[ ! "${java_version_line}" =~ version\ \"21(\.|\"|$) ]]; then
    die \
      "Java 21 est requis. Version détectée : ${java_version_line:-inconnue}" \
      "sudo pacman -S --needed jdk21-openjdk"
  fi
}

paper_jar_local() {
  local best=-1 num base f found=""
  [[ -d "${SERVER_DIR}" ]] || return 0
  while IFS= read -r f; do
    [[ -n "${f}" && -s "${f}" ]] || continue
    base="$(basename "${f}")"
    num="${base#paper-${MC_VERSION}-}"
    num="${num%.jar}"
    if [[ "${num}" =~ ^[0-9]+$ ]] && (( 10#${num} > best )); then
      best=$((10#${num}))
      found="${f}"
    fi
  done < <(find "${SERVER_DIR}" -maxdepth 1 -type f -name "paper-${MC_VERSION}-*.jar" | sort)
  printf '%s' "${found}"
}

ensure_paper() {
  local existing
  existing="$(paper_jar_local)"
  if [[ -n "${existing}" ]]; then
    PAPER_JAR="${existing}"
    printf 'Paper déjà présent : %s\n' "$(basename "${PAPER_JAR}")"
    return 0
  fi

  command -v curl >/dev/null 2>&1 || die \
    "curl est requis pour télécharger Paper." \
    "sudo pacman -S --needed curl"
  command -v python3 >/dev/null 2>&1 || die \
    "python3 est requis pour lire l'API Paper." \
    "sudo pacman -S --needed python"

  local tmp_json http_code curl_status
  tmp_json="$(mktemp)"
  set +e
  http_code="$(curl -sS -A "${USER_AGENT}" -o "${tmp_json}" -w '%{http_code}' "${API_BUILDS}")"
  curl_status=$?
  set -e
  if (( curl_status != 0 )); then
    rm -f "${tmp_json}"
    die "Impossible de joindre l'API Paper (curl ${curl_status})." "URL : ${API_BUILDS}"
  fi

  if [[ "${http_code}" == "404" ]]; then
    local message
    message="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("message","Version introuvable."))' "${tmp_json}" 2>/dev/null || true)"
    rm -f "${tmp_json}"
    die \
      "La version Minecraft ${MC_VERSION} n'est pas publiée sur Paper (HTTP 404)." \
      "${message}" \
      "URL : ${API_BUILDS}"
  fi
  if [[ "${http_code}" != "200" ]]; then
    rm -f "${tmp_json}"
    die \
      "L'API Paper a répondu HTTP ${http_code} pour Minecraft ${MC_VERSION}." \
      "Aucun build publié n'a été téléchargé." \
      "URL : ${API_BUILDS}"
  fi

  local parsed jar_name jar_url sha build_id
  if ! parsed="$(python3 - "${tmp_json}" <<'PY'
import json
import sys

data = json.load(open(sys.argv[1], encoding="utf-8"))
if isinstance(data, dict) and data.get("ok") is False:
    sys.stderr.write((data.get("message") or data.get("error") or "Réponse Paper invalide.") + "\n")
    sys.exit(2)
if not isinstance(data, list):
    sys.stderr.write("Réponse Paper inattendue (liste de builds attendue).\n")
    sys.exit(2)
stables = [
    build for build in data
    if isinstance(build, dict) and str(build.get("channel", "")).upper() == "STABLE"
]
if not stables:
    sys.stderr.write("Aucun build STABLE pour cette version.\n")
    sys.exit(3)
best = max(stables, key=lambda build: int(build["id"]))
download = (best.get("downloads") or {}).get("server:default") or {}
name = download.get("name") or ""
url = download.get("url") or ""
sha = ((download.get("checksums") or {}).get("sha256")) or ""
if not name or not url:
    sys.stderr.write("Build STABLE sans fichier server:default.\n")
    sys.exit(4)
sys.stdout.write(f"{name}\n{url}\n{sha}\n{best['id']}\n")
PY
)"; then
    rm -f "${tmp_json}"
    die "Impossible de choisir un build Paper STABLE pour Minecraft ${MC_VERSION}." "URL : ${API_BUILDS}"
  fi
  rm -f "${tmp_json}"

  jar_name="$(printf '%s\n' "${parsed}" | sed -n '1p')"
  jar_url="$(printf '%s\n' "${parsed}" | sed -n '2p')"
  sha="$(printf '%s\n' "${parsed}" | sed -n '3p')"
  build_id="$(printf '%s\n' "${parsed}" | sed -n '4p')"

  [[ "${jar_name}" =~ ^paper-${MC_VERSION}-[0-9]+\.jar$ ]] || die "Nom de JAR Paper inattendu : ${jar_name}"
  [[ "${jar_url}" == https://* ]] || die "URL de téléchargement Paper refusée."

  local dest part actual
  dest="${SERVER_DIR}/${jar_name}"
  part="${dest}.partial"
  printf 'Téléchargement de Paper %s (build STABLE %s)...\n' "${MC_VERSION}" "${build_id}"
  if ! curl -fL --retry 3 --retry-delay 2 -A "${USER_AGENT}" -o "${part}" "${jar_url}"; then
    rm -f "${part}"
    die "Échec du téléchargement de ${jar_name}." "URL : ${jar_url}"
  fi
  if [[ -n "${sha}" ]] && command -v sha256sum >/dev/null 2>&1; then
    actual="$(sha256sum "${part}" | awk '{print $1}')"
    if [[ "${actual}" != "${sha}" ]]; then
      rm -f "${part}"
      die "Somme SHA-256 incorrecte pour ${jar_name}." "attendu : ${sha}" "obtenu  : ${actual}"
    fi
  fi
  mv -f "${part}" "${dest}"
  PAPER_JAR="${dest}"
}

install_plugin() {
  local jar base plugin_src=""
  if [[ -d "${ROOT}/target" ]]; then
    while IFS= read -r jar; do
      [[ -n "${jar}" && -s "${jar}" ]] || continue
      base="$(basename "${jar}")"
      [[ "${base}" == original-* ]] && continue
      plugin_src="${jar}"
    done < <(find "${ROOT}/target" -maxdepth 1 -type f -name '*.jar' | sort)
  fi

  mkdir -p "${SERVER_DIR}/plugins"
  if [[ -n "${plugin_src}" ]]; then
    cp -f "${plugin_src}" "${SERVER_DIR}/plugins/Cantale.jar"
    printf 'Plugin copié : %s -> plugins/Cantale.jar\n' "$(basename "${plugin_src}")"
    return 0
  fi

  printf '%s\n' "Le plugin Cantale est absent."
  if ! command -v mvn >/dev/null 2>&1; then
    printf '%s\n' \
      "Maven n'est pas installé : le JAR du plugin n'a pas été compilé. Le serveur Paper démarre quand même, sans Cantale." \
      "sudo pacman -S --needed jdk21-openjdk maven" \
      "Puis, à la racine du dépôt : mvn -q -DskipTests package"
  else
    printf '%s\n' \
      "Compilez-le avec : mvn -q -DskipTests package" \
      "Le serveur Paper démarre quand même, sans Cantale."
  fi
}

print_addresses() {
  local lan=""
  printf '\nAdresse à coller dans Minecraft :\n  127.0.0.1:25565\n'
  if command -v ip >/dev/null 2>&1; then
    lan="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit }}' || true)"
  fi
  if [[ -z "${lan}" ]]; then
    lan="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  fi
  if [[ -n "${lan}" ]]; then
    printf 'Adresse sur le réseau local :\n  %s:25565\n' "${lan}"
  fi
  printf '\nLes lignes ci-dessous sont la console du serveur. Ctrl+C l'\''arrête.\n\n'
}

require_java21
mkdir -p "${SERVER_DIR}/plugins"
PAPER_JAR=""
ensure_paper
printf 'eula=true\n' > "${SERVER_DIR}/eula.txt"
install_plugin
print_addresses

cd "${SERVER_DIR}"
exec java -Xms1G -Xmx2G -jar "${PAPER_JAR}" nogui
