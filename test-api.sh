#!/bin/bash

# AI Diagram Generator - API Test Script
# Tests all endpoints and demonstrates functionality

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}================================${NC}"
echo -e "${YELLOW}AI Diagram Generator API Tests${NC}"
echo -e "${YELLOW}================================${NC}\n"

# Test 1: Generate Class Diagram from Text
echo -e "${GREEN}Test 1: Generate Class Diagram from Natural Language${NC}"
curl -s -X POST ${BASE_URL}/api/diagrams/from-text \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Describe a login system with user, auth service, and database"
  }' | python3 -m json.tool
echo -e "\n"

# Test 2: Generate Sequence Diagram from Text
echo -e "${GREEN}Test 2: Generate Sequence Diagram (keyword detection)${NC}"
curl -s -X POST ${BASE_URL}/api/diagrams/from-text \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Show the sequence of user authentication flow with login request and token generation"
  }' | python3 -m json.tool
echo -e "\n"

# Test 3: Generate from XML
echo -e "${GREEN}Test 3: Generate Diagram from XML${NC}"
curl -s -X POST ${BASE_URL}/api/diagrams/from-xml \
  -H "Content-Type: application/json" \
  -d '{
    "xml": "<system><component name=\"Frontend\"/><component name=\"Backend\"/><component name=\"Database\"/></system>"
  }' | python3 -m json.tool
echo -e "\n"

# Test 4: Generate from URL
echo -e "${GREEN}Test 4: Generate Architecture Diagram from URL${NC}"
curl -s -X POST ${BASE_URL}/api/diagrams/from-url \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://github.com/spring-projects/spring-boot"
  }' | python3 -m json.tool
echo -e "\n"

# Test 5: Validation Error - Text Too Short
echo -e "${YELLOW}Test 5: Validation Error (text too short)${NC}"
curl -s -X POST ${BASE_URL}/api/diagrams/from-text \
  -H "Content-Type: application/json" \
  -d '{
    "text": "short"
  }' | python3 -m json.tool
echo -e "\n"

# Test 6: Validation Error - Blank Text
echo -e "${YELLOW}Test 6: Validation Error (blank text)${NC}"
curl -s -X POST ${BASE_URL}/api/diagrams/from-text \
  -H "Content-Type: application/json" \
  -d '{
    "text": ""
  }' | python3 -m json.tool
echo -e "\n"

# Test 7: Validation Error - Invalid URL
echo -e "${YELLOW}Test 7: Validation Error (invalid URL format)${NC}"
curl -s -X POST ${BASE_URL}/api/diagrams/from-url \
  -H "Content-Type: application/json" \
  -d '{
    "url": "not-a-valid-url"
  }' | python3 -m json.tool
echo -e "\n"

# Test 8: Generate ER Diagram (keyword detection)
echo -e "${GREEN}Test 8: Generate ER Diagram (keyword detection)${NC}"
curl -s -X POST ${BASE_URL}/api/diagrams/from-text \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Create a database schema with users table, orders table, and products table with relationships"
  }' | python3 -m json.tool
echo -e "\n"

echo -e "${YELLOW}================================${NC}"
echo -e "${GREEN}✅ All Tests Complete!${NC}"
echo -e "${YELLOW}================================${NC}"
echo -e "\nAPI Documentation: ${BASE_URL}/swagger-ui.html"
echo -e "API Docs JSON: ${BASE_URL}/api-docs\n"
