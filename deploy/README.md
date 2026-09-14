# Oracle Cloud Free Tier — Calyth Deployment Guide

## 1. Create VM

1. Go to [cloud.oracle.com](https://cloud.oracle.com)
2. **Compute → Instances → Create Instance**
3. Settings:
   - **Name**: `calyth`
   - **Image**: Ubuntu 22.04 (or any Linux)
   - **Shape**: VM.Standard.A1.Flex (ARM — always free)
   - **OCPU**: 4 (max free)
   - **RAM**: 24 GB (max free)
   - **Boot Volume**: 200 GB
4. **Add SSH key** — paste your public key
5. **Create** — note the public IP

## 2. Connect

```bash
ssh -i ~/.ssh/your_key ubuntu@<PUBLIC_IP>
```

## 3. Deploy

```bash
# Clone and deploy
git clone https://github.com/chila254/Calyth.git
cd Calyth

# Create .env with your API keys
cp deploy/.env.example .env
nano .env   # paste your keys

# Build and start with Docker
sudo apt-get update && sudo apt-get install -y docker.io git
sudo systemctl enable docker && sudo systemctl start docker

# Install docker-compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" \
  -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Build and run
docker-compose up --build -d
```

## 4. Open Firewall

In Oracle Cloud console:
1. Go to your VM → **Subnet** → **Default Security List**
2. **Add Ingress Rules**:
   - **Source CIDR**: `0.0.0.0/0`
   - **Destination Port**: `80`

## 5. Test

```bash
curl http://<YOUR_PUBLIC_IP>/health
# Should return: OK
```

Open `http://<YOUR_PUBLIC_IP>` in your browser.

## 6. Update Android App

In the Android app settings, enter your server URL:
```
http://<YOUR_PUBLIC_IP>
```

## Commands

```bash
# View logs
docker-compose logs -f

# Restart
docker-compose restart

# Stop
docker-compose down

# Update (after git pull)
docker-compose up --build -d
```
