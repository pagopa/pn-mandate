cat docs/openapi/pn-mandate-internal-v1.yaml \
    | grep -v "# NO EXTERNAL" \
    | sed -e '/# ONLY EXTERNAL/s/^#//' \
    > docs/openapi/pn-mandate-external-web-v1.yaml