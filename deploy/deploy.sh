#!/bin/bash
# Calyth — Oracle Cloud Deployment Script
# Run this ON YOUR ORACLE CLOUD VM

set -e

echo "=== Calyth Deployment ==="

# 1. Install Docker
echo "[1/5] Installing Docker..."
if ! command -v docker &> /dev/null; then
    apt-get update
    apt-get install -y docker.io git
    systemctl enable docker
    systemctl start docker
    usermod -aG docker ubuntu 2>/dev/null || true
fi

# 2. Install Docker Compose
echo "[2/5] Installing Docker Compose..."
if ! command -v docker-compose &> /dev/null; then
    curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" \
        -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
fi

# 3. Clone repo
echo "[3/5] Cloning Calyth..."
cd /opt
if [ -d "Calyth" ]; then
    cd Calyth && git pull
else
    git clone https://github.com/chila254/Calyth.git
    cd Calyth
fi

# 4. Setup .env
echo "[4/5] Setting up environment..."
if [ ! -f ".env" ]; then
    echo "Creating .env from template..."
    cp deploy/.env.example .env
    echo ""
    echo ">>> EDIT /opt/Calyth/.env WITH YOUR API KEYS <<<"
    echo ">>> Then re-run this script <<<"
    exit 1
fi

# 5. Build and start
echo "[5/5] Building and starting..."
docker-compose down 2>/dev/null || true
docker-compose up --build -d

echo ""
echo "=== Calyth is running! ==="
echo "  Frontend: http://$(curl -s ifconfig.me)"
echo "  Health:   http://$(curl -s ifconfig.me)/health"
echo ""
echo "  Logs: docker-compose logs -f"
echo "  Stop:  docker-compose down"
