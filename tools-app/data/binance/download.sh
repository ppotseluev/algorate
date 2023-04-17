#!/bin/bash

# This is a simple script to download klines by given parameters.

# space separated values
symbols=$2
intervals=("1m")
years=$1
months=(01 02 03 04 05 06 07 08 09 10 11 12)

force=false

for arg in "$@"
do
  if [ "$arg" = "-f" ]; then
    force=true
    break
  fi
done

baseurl="https://data.binance.vision/data/spot/monthly/klines"

cd archive || exit 1

for symbol in ${symbols[@]}; do
  for interval in ${intervals[@]}; do
    for year in ${years[@]}; do
      target_dir="${symbol}_${year}"
      if [ "$force" = false ] && [ -d "${target_dir}" ] && [ "$(ls -A "$target_dir")" ]; then
        echo "${target_dir} already exists"
      else
        for month in ${months[@]}; do
          file_name=${symbol}-${interval}-${year}-${month}
          zip_file_name="${file_name}.zip"
          url="${baseurl}/${symbol}/${interval}/${zip_file_name}"
          response=$(wget --server-response -q ${url} 2>&1 | awk 'NR==1{print $2}')
          if [ ${response} == '404' ]; then
            echo "File not exist: ${url}"
          else
            echo "downloaded: ${url}"
            unzip -q "${zip_file_name}" -d "${target_dir}"
            rm "${zip_file_name}"
            csv_file_name="${file_name}.csv"
            mv "${target_dir}/${csv_file_name}" "${target_dir}/$(echo "${csv_file_name}" | sed -E "s/${symbol}-${interval}-([0-9]{4})-([0-9]{2}).csv/\1\2.csv/")"
          fi
        done
      fi
    done
  done
done
