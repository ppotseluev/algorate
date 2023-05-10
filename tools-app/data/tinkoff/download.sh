figi_list=tinkoff/figi.txt
token=${TINKOFF_TOKEN}
url=https://invest-public-api.tinkoff.ru/history-data
function download {
  cd "archive/1m" || exit 1
  local figi=$1
  local year=$2
  local file_name=${figi}_${year}.zip
  local target_dir="${figi}_${year}"
  if [ -d "${target_dir}" ]; then
    echo "${target_dir} already exists, skipping"
    return 0
  fi
  echo "downloading $figi for year $year"
  local response_code=$(curl -s --location "${url}?figi=${figi}&year=${year}" \
    -H "Authorization: Bearer ${token}" -o "${file_name}" -w '%{http_code}\n')
  #  if [ "$response_code" = "200" ]; then
  #    ((year--))
  #    download "$figi" "$year";
  #  fi
  if [ "$response_code" = "200" ]; then
    unzip -q "${file_name}" -d "${target_dir}"
    rm "${figi}_${year}.zip"
    cd "${target_dir}" || exit 1
    for file in ./*; do
      mv -- "$file" "${file:39}" #TODO improve prefix removing
    done
  fi
  # Если превышен лимит запросов в минуту (30) - повторяем запрос.
  if [ "$response_code" = "429" ]; then
    echo "rate limit exceed. sleep 5"
    sleep 5
    download "$figi" "$year"
    return 0
  fi
  # Если невалидный токен - выходим.
  if [ "$response_code" = "401" ] || [ "$response_code" = "500" ]; then
    echo 'invalid token'
    exit 1
  fi
  # Если данные по инструменту за указанный год не найдены.
  if [ "$response_code" = "404" ]; then
    echo "data not found for figi=${figi}, year=${year}, skipped"
  elif [ "$response_code" != "200" ]; then
    # В случае другой ошибки - просто напишем ее в консоль и выйдем.
    echo "unspecified error with code: ${response_code}"
    exit 1
  fi
}

if [ $# -eq 0 ]; then
  echo "Error: missing required parameter 'year'"
  echo "Usage: $0 [year]"
  exit 1
fi

current_dir="$(pwd)"
year=$1
instrument=$2
if [ -z "$instrument" ]; then
  echo "Downloading from figi list"
  while read -r figi; do
    download "$figi" "$year"
    cd "$current_dir" || exit 1
  done <${figi_list}
else
  download "$instrument" "$year"
  cd "$current_dir" || exit 1
fi
