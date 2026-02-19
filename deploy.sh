#!/bin/bash

# Deploy script for Bewerbung AI application
# Stops the application before building/running to save memory (1GB server)

set -e  # Exit on error

APP_NAME="bewerbung-ai"
APP_JAR="bewerbung-ai-1.0.0.jar"
APP_DIR="/home/opc/bewerbung-ai"
PID_FILE="$APP_DIR/app.pid"
LOG_FILE="$APP_DIR/app.log"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

echo_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to stop the application
stop_app() {
    echo_info "Stopping application..."
    
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo_info "Found running process (PID: $PID), stopping..."
            kill $PID || true
            sleep 2
            
            # Force kill if still running
            if ps -p $PID > /dev/null 2>&1; then
                echo_warn "Process still running, force killing..."
                kill -9 $PID || true
                sleep 1
            fi
        fi
        rm -f "$PID_FILE"
    fi
    
    # Also try to find and kill by process name
    pkill -9 -f "$APP_JAR" || true
    pkill -9 -f "java.*bewerbung" || true
    sleep 1
    
    # Free memory
    sync
    sudo sh -c 'echo 3 > /proc/sys/vm/drop_caches' 2>/dev/null || true
    
    echo_info "Application stopped"
}

# Function to build the project
build_app() {
    echo_info "Building application..."
    cd "$APP_DIR"
    
    # Set up environment
    export PATH=/home/opc/jdk-21.0.2/bin:/home/opc/apache-maven-3.9.5/bin:$PATH
    export MAVEN_OPTS="-Xmx256m -Xms128m"
    
    # Clean and build
    mvn clean package -DskipTests
    
    if [ ! -f "target/$APP_JAR" ]; then
        echo_error "Build failed: JAR file not found"
        exit 1
    fi
    
    echo_info "Build completed successfully"
}

# Function to start the application
start_app() {
    echo_info "Starting application..."
    cd "$APP_DIR"
    
    # Check if JAR exists
    if [ ! -f "target/$APP_JAR" ]; then
        echo_error "JAR file not found. Please build first."
        exit 1
    fi
    
    # Set up Java environment
    export PATH=/home/opc/jdk-21.0.2/bin:$PATH
    
    # Start application in background
    nohup java -Xmx512m -Xms256m \
        -Dorg.apache.pdfbox.fontcache.disabled=true \
        -jar "target/$APP_JAR" \
        > "$LOG_FILE" 2>&1 &
    
    APP_PID=$!
    echo $APP_PID > "$PID_FILE"
    
    echo_info "Application started with PID: $APP_PID"
    echo_info "Logs are being written to: $LOG_FILE"
    
    # Wait a bit and check if process is still running
    sleep 5
    if ! ps -p $APP_PID > /dev/null 2>&1; then
        echo_error "Application failed to start. Check logs: $LOG_FILE"
        tail -30 "$LOG_FILE"
        exit 1
    fi
    
    echo_info "Application is running successfully"
}

# Function to show application status
status_app() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo_info "Application is running (PID: $PID)"
            ps -p $PID -o pid,rss,vsz,comm --no-headers | awk '{printf "  RSS: %.1f MB, VSZ: %.1f MB\n", $2/1024, $3/1024}'
            return 0
        else
            echo_warn "PID file exists but process is not running"
            rm -f "$PID_FILE"
            return 1
        fi
    else
        echo_warn "Application is not running (no PID file)"
        return 1
    fi
}

# Main script logic
case "${1:-}" in
    stop)
        stop_app
        ;;
    build)
        stop_app
        build_app
        ;;
    start)
        stop_app
        start_app
        ;;
    restart)
        stop_app
        start_app
        ;;
    deploy)
        stop_app
        build_app
        start_app
        ;;
    status)
        status_app
        ;;
    logs)
        if [ -f "$LOG_FILE" ]; then
            tail -f "$LOG_FILE"
        else
            echo_error "Log file not found: $LOG_FILE"
        fi
        ;;
    *)
        echo "Usage: $0 {stop|build|start|restart|deploy|status|logs}"
        echo ""
        echo "Commands:"
        echo "  stop    - Stop the application"
        echo "  build   - Stop app, build project"
        echo "  start   - Stop app, start application"
        echo "  restart - Stop and start application"
        echo "  deploy  - Stop, build, and start (full deployment)"
        echo "  status  - Check application status"
        echo "  logs    - Show application logs (tail -f)"
        exit 1
        ;;
esac

