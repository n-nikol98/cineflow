# --- Runtime image ---
FROM node:20-alpine
WORKDIR /app

COPY package.json ./
COPY server.js ./

EXPOSE 9090
CMD ["node", "server.js"]
