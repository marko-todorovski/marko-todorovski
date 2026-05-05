#!/bin/bash
echo "=== Test 1: POST /api/diagrams/from-text (valid) ==="
curl -s -X POST http://localhost:8080/api/diagrams/from-text \
  -H "Content-Type: application/json" \
  -d '{"text":"Describe a login system with user auth service and database"}'
echo ""
echo "STATUS: $?"
echo ""

echo "=== Test 2: POST /api/diagrams/from-xml (valid) ==="
curl -s -X POST http://localhost:8080/api/diagrams/from-xml \
  -H "Content-Type: application/json" \
  -d '{"xml":"<system><user/><auth/><db/></system>"}'
echo ""
echo "STATUS: $?"
echo ""

echo "=== Test 3: POST /api/diagrams/from-url (valid) ==="
curl -s -X POST http://localhost:8080/api/diagrams/from-url \
  -H "Content-Type: application/json" \
  -d '{"url":"https://github.com/example/repo"}'
echo ""
echo "STATUS: $?"
echo ""

echo "=== Test 4: Validation - text too short ==="
curl -s -X POST http://localhost:8080/api/diagrams/from-text \
  -H "Content-Type: application/json" \
  -d '{"text":"short"}'
echo ""
echo ""

echo "=== Test 5: Validation - invalid URL ==="
curl -s -X POST http://localhost:8080/api/diagrams/from-url \
  -H "Content-Type: application/json" \
  -d '{"url":"not-a-url"}'
echo ""
echo ""

echo "=== Test 6: Validation - blank XML ==="
curl -s -X POST http://localhost:8080/api/diagrams/from-xml \
  -H "Content-Type: application/json" \
  -d '{"xml":""}'
echo ""
echo ""

echo "=== Test 7: Swagger UI ==="
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/swagger-ui.html)
echo "Swagger UI: HTTP $HTTP_CODE"

echo "=== Test 8: API Docs ==="
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api-docs)
echo "API Docs: HTTP $HTTP_CODE"

echo ""
echo "=== ALL TESTS COMPLETE ==="
