#!/bin/bash

# Script to clean up unnecessary files on the server

SSH_KEY="$HOME/.ssh/ocivm_key"
SERVER_USER="opc"
SERVER_HOST="130.162.224.203"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== Server Cleanup Script ===${NC}\n"

# Check SSH key
if [ ! -f "$SSH_KEY" ]; then
    echo -e "${RED}ERROR: SSH key not found at $SSH_KEY${NC}"
    exit 1
fi

echo -e "${YELLOW}Checking for unnecessary files...${NC}\n"

# Check what can be cleaned
ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_HOST" << 'ENDSSH'
    echo "=== Files that can be removed ==="
    echo ""
    
    # RPM package (JDK installer - not needed, JDK is already installed from tar.gz)
    if [ -f /home/opc/jdk-21-headless-21.0.8-12.el9.x86_64.rpm ]; then
        SIZE=$(du -h /home/opc/jdk-21-headless-21.0.8-12.el9.x86_64.rpm | cut -f1)
        echo "1. JDK RPM package: /home/opc/jdk-21-headless-21.0.8-12.el9.x86_64.rpm ($SIZE)"
        echo "   Reason: JDK already installed from tar.gz, RPM package not needed"
    fi
    
    echo ""
    echo "=== Optional cleanup (Maven cache) ==="
    if [ -d /home/opc/.m2 ]; then
        SIZE=$(du -sh /home/opc/.m2 | cut -f1)
        echo "2. Maven cache: /home/opc/.m2 ($SIZE)"
        echo "   Reason: Can be cleaned, but will slow down next build"
        echo "   Recommendation: Keep it for faster builds"
    fi
    
    echo ""
    echo "=== Current disk usage ==="
    df -h /home | tail -1
    echo ""
    echo "Total in /home/opc:"
    du -sh /home/opc
ENDSSH

echo ""
read -p "Do you want to remove the JDK RPM package (64MB)? (y/N): " confirm

if [[ $confirm == [yY] || $confirm == [yY][eE][sS] ]]; then
    echo -e "\n${YELLOW}Removing JDK RPM package...${NC}"
    ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_HOST" "rm -f /home/opc/jdk-21-headless-21.0.8-12.el9.x86_64.rpm && echo '✓ Removed' || echo '✗ Error removing file'"
    
    echo -e "\n${GREEN}Cleanup completed!${NC}"
    echo ""
    ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_HOST" "echo 'New disk usage:' && du -sh /home/opc && df -h /home | tail -1"
else
    echo -e "\n${YELLOW}Cleanup cancelled.${NC}"
fi

echo ""
read -p "Do you want to clean Maven cache (73MB)? This will slow down next build. (y/N): " confirm_maven

if [[ $confirm_maven == [yY] || $confirm_maven == [yY][eE][sS] ]]; then
    echo -e "\n${YELLOW}Cleaning Maven cache...${NC}"
    ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_HOST" "rm -rf /home/opc/.m2/repository/* && echo '✓ Maven cache cleaned' || echo '✗ Error cleaning cache'"
    
    echo -e "\n${GREEN}Maven cache cleaned!${NC}"
    echo ""
    ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_HOST" "echo 'New disk usage:' && du -sh /home/opc && df -h /home | tail -1"
else
    echo -e "\n${YELLOW}Maven cache kept (recommended for faster builds).${NC}"
fi

echo ""
echo -e "${GREEN}Done!${NC}"

