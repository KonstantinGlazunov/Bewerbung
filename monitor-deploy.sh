#!/bin/bash

# Script to monitor deployment process on server in real-time

SSH_KEY="$HOME/.ssh/ocivm_key"
SERVER_USER="opc"
SERVER_HOST="130.162.224.203"
APP_DIR="/home/opc/bewerbung-ai"
OLD_APP_DIR="/opt/bewerbung-ai"
OLD_LOG="/home/opc/app.log"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== Bewerbung AI Deployment Monitor ===${NC}\n"

# Check SSH key
if [ ! -f "$SSH_KEY" ]; then
    echo "ERROR: SSH key not found at $SSH_KEY"
    exit 1
fi

# Function to show menu
show_menu() {
    echo -e "${GREEN}Select monitoring option:${NC}"
    echo "1) Watch build log (real-time)"
    echo "2) Watch application log (real-time)"
    echo "3) Watch both logs (split screen with tmux)"
    echo "4) Watch processes (maven/java)"
    echo "5) Show deployment status"
    echo "6) Show recent build log (last 50 lines)"
    echo "7) Show recent app log (last 50 lines)"
    echo "8) Interactive SSH session"
    echo "0) Exit"
    echo ""
    read -p "Choice: " choice
}

# Main loop
while true; do
    show_menu
    
    case $choice in
        1)
            echo -e "\n${YELLOW}Watching build log (Ctrl+C to stop)...${NC}\n"
            ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_HOST" "if [ -f $APP_DIR/build.log ]; then tail -f $APP_DIR/build.log; else echo 'Build log not found at $APP_DIR/build.log'; echo 'Checking if build is in progress...'; ps aux | grep maven | grep -v grep || echo 'No build process found'; fi"
            ;;
        2)
            echo -e "\n${YELLOW}Watching application log (Ctrl+C to stop)...${NC}\n"
            ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_HOST" "if [ -f $APP_DIR/app.log ]; then tail -f $APP_DIR/app.log; elif [ -f $OLD_LOG ]; then echo 'Using old log location: $OLD_LOG'; tail -f $OLD_LOG; else echo 'App log not found. Checking running processes...'; ps aux | grep bewerbung | grep -v grep || echo 'No application running'; fi"
            ;;
        3)
            echo -e "\n${YELLOW}Setting up tmux session for split-screen monitoring...${NC}"
            ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_HOST" << 'ENDSSH'
                tmux kill-session -t deploy-monitor 2>/dev/null || true
                tmux new-session -d -s deploy-monitor
                tmux split-window -h -t deploy-monitor
                tmux send-keys -t deploy-monitor:0.0 "tail -f /home/opc/bewerbung-ai/build.log" C-m
                tmux send-keys -t deploy-monitor:0.1 "tail -f /home/opc/bewerbung-ai/app.log" C-m
                tmux attach -t deploy-monitor
ENDSSH
            ;;
        4)
            echo -e "\n${YELLOW}Watching processes (Ctrl+C to stop)...${NC}\n"
            ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_HOST" "watch -n 1 'ps aux | grep -E \"maven|java|bewerbung\" | grep -v grep'"
            ;;
        5)
            echo -e "\n${YELLOW}Deployment Status:${NC}\n"
            ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_HOST" << 'ENDSSH'
                echo "=== New deployment location ==="
                /home/opc/deploy.sh status 2>/dev/null || echo "Deploy script not available or app not running"
                echo ""
                echo "=== Running processes ==="
                ps aux | grep -E "bewerbung|java.*jar" | grep -v grep || echo "No bewerbung processes found"
                echo ""
                echo "=== Log files ==="
                if [ -f /home/opc/bewerbung-ai/app.log ]; then
                    echo "New app log: /home/opc/bewerbung-ai/app.log ($(wc -l < /home/opc/bewerbung-ai/app.log) lines)"
                fi
                if [ -f /home/opc/app.log ]; then
                    echo "Old app log: /home/opc/app.log ($(wc -l < /home/opc/app.log) lines)"
                fi
                if [ -f /home/opc/bewerbung-ai/build.log ]; then
                    echo "Build log: /home/opc/bewerbung-ai/build.log ($(wc -l < /home/opc/bewerbung-ai/build.log) lines)"
                fi
                echo ""
                echo "=== Memory status ==="
                free -h
ENDSSH
            ;;
        6)
            echo -e "\n${YELLOW}Recent build log (last 50 lines):${NC}\n"
            ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_HOST" "if [ -f $APP_DIR/build.log ]; then tail -50 $APP_DIR/build.log; else echo 'Build log not found at $APP_DIR/build.log'; fi"
            ;;
        7)
            echo -e "\n${YELLOW}Recent app log (last 50 lines):${NC}\n"
            ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_HOST" "if [ -f $APP_DIR/app.log ]; then tail -50 $APP_DIR/app.log; elif [ -f $OLD_LOG ]; then echo 'Using old log location:'; tail -50 $OLD_LOG; else echo 'App log not found in $APP_DIR/app.log or $OLD_LOG'; fi"
            ;;
        8)
            echo -e "\n${YELLOW}Connecting to server...${NC}"
            echo "Use 'exit' to return"
            ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_HOST"
            ;;
        0)
            echo "Exiting..."
            exit 0
            ;;
        *)
            echo "Invalid choice"
            ;;
    esac
    
    echo ""
    read -p "Press Enter to continue..."
    clear
done

