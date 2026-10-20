#!/usr/bin/env bash
# Android Studio'da açmak için yerel çalışma alanı hazırlar:
#   ./setup-local.sh   →  ./cs klasörünü Android Studio ile açın, "player" yapılandırmasını çalıştırın.
set -euo pipefail
cd "$(dirname "$0")"
CS_REF=$(grep -m1 'CS_REF:' .github/workflows/build.yml | awk '{print $2}')

if [ ! -d cs ]; then
  git clone --filter=blob:none https://github.com/recloudstream/cloudstream.git cs
fi
git -C cs fetch --quiet origin && git -C cs checkout --quiet "$CS_REF"

(cd js && npm install --no-audit --no-fund && npm run build && npm test)

# player modülünü kopyalamak yerine bağla, böylece düzenlemeler doğrudan bu repoya yansır
rm -rf cs/player
ln -s ../player cs/player
grep -q 'include(":player")' cs/settings.gradle.kts || printf '\ninclude(":player")\n' >> cs/settings.gradle.kts
echo "Hazır. Derlemek için:  cd cs && ./gradlew :player:assembleDebug"
