#!/bin/bash
npx html-minifier-terser index.full.html -o index.min.html \
  --collapse-whitespace \
  --custom-attr-collapse \
  --remove-comments \
  --minify-js true \
  --minify-css true
sed -r 's/ {2,}/  /g' index.min.html > index.html
rm index.min.html
sed -i '' "s|http://localhost:8001/mock-data.json|/api/v1/spots|g" index.html
