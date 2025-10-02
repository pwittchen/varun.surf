#!/bin/bash

# this script builds and runs the whole app

echo "🚧  starting frontend build..."
npx html-minifier-terser index.full.html -o index.min.html \
  --collapse-whitespace \
  --custom-attr-collapse \
  --remove-comments \
  --minify-js true \
  --minify-css true
sed -r 's/ {2,}/  /g' index.min.html > index.html
rm index.min.html
cp index.html src/main/resources/static/index.html
rm index.html
echo "✅  frontend was build successfully"

echo "🚧  starting backend build..."
./gradlew clean bootJar
echo "✅  backend was build successfully"

echo "🚀  starting the app"
java --enable-preview -jar build/libs/*.jar

echo "🛑  app was stopped"