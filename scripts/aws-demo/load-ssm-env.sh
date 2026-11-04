#!/usr/bin/env bash
# Merge the AWS demo provider settings and secrets into .env.prod (issue #238).
#
# Run on the instance, from anywhere. Reads SecureString parameters under /aegis-demo/ (set
# SSM_PREFIX to change it) with the instance role, then rewrites those keys in .env.prod.
# Secret values are never printed; only key names are.
#
# Create the parameters in AWS Systems Manager, Parameter Store, type SecureString:
#   /aegis-demo/SENDGRID_API_KEY               Mailtrap Send API token
#   /aegis-demo/SENDGRID_FROM_ADDRESS          sender on a Mailtrap-verified domain
#   /aegis-demo/VONAGE_WHATSAPP_API_KEY
#   /aegis-demo/VONAGE_WHATSAPP_API_SECRET
#   /aegis-demo/VONAGE_WHATSAPP_FROM_NUMBER    WhatsApp sandbox number, digits only
#   /aegis-demo/AGGREGATION_LLM_GROQ_API_KEY
# Optional: AGGREGATION_LLM_GROQ_MODEL, NOTIFICATION_AGGREGATION_WINDOW.
#
# All-or-nothing: if a required parameter is missing, nothing is changed, because selecting a
# vendor without its credentials would stop the notification service from starting.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT/.env.prod"
DEFAULTS="$ROOT/scripts/aws-demo/demo-providers.env"
PREFIX="${SSM_PREFIX:-/aegis-demo}"
REQUIRED=(SENDGRID_API_KEY SENDGRID_FROM_ADDRESS VONAGE_WHATSAPP_API_KEY VONAGE_WHATSAPP_API_SECRET
          VONAGE_WHATSAPP_FROM_NUMBER AGGREGATION_LLM_GROQ_API_KEY)
OPTIONAL=(AGGREGATION_LLM_GROQ_MODEL NOTIFICATION_AGGREGATION_WINDOW)

die() { echo "error: $*" >&2; exit 1; }
[[ -f "$ENV_FILE" ]] || die "$ENV_FILE not found; run scripts/deploy init first"

# Prints "NAME<TAB>VALUE" per parameter. SSM_FAKE_FILE replaces the AWS call in tests.
fetch_params() {
  if [[ -n "${SSM_FAKE_FILE:-}" ]]; then cat "$SSM_FAKE_FILE"; return; fi
  command -v aws >/dev/null || snap install aws-cli --classic >/dev/null
  local token region
  token="$(curl -fsS -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')"
  region="$(curl -fsS -H "X-aws-ec2-metadata-token: $token" http://169.254.169.254/latest/meta-data/placement/region)"
  aws ssm get-parameters-by-path --region "$region" --path "$PREFIX/" --with-decryption \
    --query 'Parameters[].[Name,Value]' --output text
}

declare -A VALUES=()
while IFS=$'\t' read -r name value; do
  [[ -n "$name" ]] && VALUES["${name##*/}"]="$value"
done < <(fetch_params)

missing=()
for key in "${REQUIRED[@]}"; do
  [[ -n "${VALUES[$key]:-}" ]] || missing+=("$key")
done
if ((${#missing[@]})); then
  echo "Missing SecureString parameters under $PREFIX/: ${missing[*]}" >&2
  echo "Nothing was changed." >&2
  exit 1
fi

declare -A UPDATES=()
while IFS= read -r line; do
  [[ "$line" =~ ^[A-Z_]+= ]] && UPDATES["${line%%=*}"]="${line#*=}"
done < "$DEFAULTS"
for key in "${REQUIRED[@]}" "${OPTIONAL[@]}"; do
  [[ -n "${VALUES[$key]:-}" ]] && UPDATES["$key"]="${VALUES[$key]}"
done

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
while IFS= read -r line || [[ -n "$line" ]]; do
  if [[ "$line" =~ ^([A-Z_]+)= && -n "${UPDATES[${BASH_REMATCH[1]}]+x}" ]]; then
    continue  # replaced below
  fi
  printf '%s\n' "$line"
done < "$ENV_FILE" > "$tmp"
for key in "${!UPDATES[@]}"; do
  value="${UPDATES[$key]}"
  [[ "$value" != *"'"* ]] || die "$key contains a single quote, which .env.prod cannot hold"
  printf "%s='%s'\n" "$key" "$value" >> "$tmp"
done
install -m 600 "$tmp" "$ENV_FILE"
echo "Updated $ENV_FILE: ${!UPDATES[*]}"
