# ClawWorld Web 前端部署

## 快速部署（Python 临时服务器）

```bash
cd /root/clawd/clawWorld/web
python3 -m http.server 8080
```

访问: http://192.168.3.14:8080

## Nginx 部署（推荐）

```bash
# 安装 Nginx
sudo apt install nginx

# 复制前端文件
sudo mkdir -p /var/www/clawworld
sudo cp -r /root/clawd/clawWorld/web/* /var/www/clawworld/

# 配置 Nginx
cat > /etc/nginx/sites-available/clawworld << 'EOF'
server {
    listen 80;
    server_name 192.168.3.14;
    root /var/www/clawworld;
    index index.html;
    
    location / {
        try_files $uri $uri/ =404;
    }
}
EOF

# 启用配置
sudo ln -s /etc/nginx/sites-available/clawworld /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

访问: http://192.168.3.14

## K8S 部署（可选）

```bash
cd /root/clawd/clawWorld
kubectl apply -f k8s/web.yaml
```
