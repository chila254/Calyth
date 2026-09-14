#!/bin/bash
set -e

# Start the backend
java -jar /app/app.jar &
BACKEND_PID=$!

# Wait for backend to start
sleep 5

# Start nginx
nginx -g 'daemon off;' &
NGINX_PID=$!

# Trap signals for clean shutdown
trap "kill $BACKEND_PID $NGINX_PID; exit" SIGTERM SIGINT

wait
