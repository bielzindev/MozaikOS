#!/usr/bin/env bash
# Downloads the weather background photos listed in third_party/weather-photos/photos.tsv
# (public domain / CC0, Wikimedia Commons) into app/src/main/assets/weather/<key>.jpg.
# Run by CI before the build; the app falls back to its drawn sky for any missing photo.
set -u
out=app/src/main/assets/weather
mkdir -p "$out"
tail -n +2 third_party/weather-photos/photos.tsv | while IFS=$'\t' read -r key file license author source url; do
  for try in 1 2 3; do
    if curl -sSfL -m 120 -A "ZUNEX-launcher-build/1.0 (https://github.com/joaogabrielsilvademourap3-spec/zunex)" -o "/tmp/$key.jpg" "$url"; then break; fi
    sleep $((try * 10))
  done
  if [ -s "/tmp/$key.jpg" ]; then
    if command -v convert >/dev/null; then
      convert "/tmp/$key.jpg" -resize '1080x1080^' -strip -quality 82 "$out/$key.jpg"
    else
      cp "/tmp/$key.jpg" "$out/$key.jpg"
    fi
    echo "photo $key: $(stat -c %s "$out/$key.jpg") bytes"
  else
    echo "photo $key: download failed (the drawn sky is used)"
  fi
  sleep 2
done
exit 0
