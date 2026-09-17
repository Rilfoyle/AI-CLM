#!/usr/bin/env sh
# Source this file from the repository root: `. infra/env.sh`.
# It loads only CLM_* variables and preserves JDBC query strings containing `&`.

clm_env_file="${CLM_ENV_FILE:-infra/.env}"
if [ ! -f "$clm_env_file" ]; then
  echo "Missing $clm_env_file; copy infra/.env.example to infra/.env first." >&2
  return 1 2>/dev/null || exit 1
fi

while IFS='=' read -r clm_env_key clm_env_value; do
  case "$clm_env_key" in
    CLM_*)
      # Docker Compose accepts optional quotes in .env values; remove one matching pair for the shell.
      case "$clm_env_value" in
        \'*\') clm_env_value=${clm_env_value#\'}; clm_env_value=${clm_env_value%\'} ;;
        \"*\") clm_env_value=${clm_env_value#\"}; clm_env_value=${clm_env_value%\"} ;;
      esac
      export "$clm_env_key=$clm_env_value"
      ;;
  esac
done < "$clm_env_file"

unset clm_env_file clm_env_key clm_env_value
