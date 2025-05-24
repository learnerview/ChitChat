# 🛠️ ChitChat Setup Guide

Complete step-by-step installation and configuration guide for deploying ChitChat in development and production environments.

## 📋 Table of Contents

1. [System Requirements](#system-requirements)
2. [Development Setup](#development-setup)
3. [Production Deployment](#production-deployment)
4. [Database Configuration](#database-configuration)
5. [Security Setup](#security-setup)
6. [Environment Configuration](#environment-configuration)
7. [Troubleshooting](#troubleshooting)

## 🔧 System Requirements

### Minimum Requirements
- **Java**: JDK 17 or higher
- **MongoDB**: Version 4.4 or higher
- **Maven**: Version 3.6 or higher
- **Memory**: Minimum 2GB RAM (4GB recommended)
- **Storage**: Minimum 5GB free space

### Recommended Requirements
- **Java**: JDK 21 (LTS)
- **MongoDB**: Version 6.0 or higher
- **Maven**: Version 3.8 or higher
- **Memory**: 8GB RAM
- **Storage**: 20GB SSD

## 🚀 Development Setup

### Step 1: Install Java Development Kit

#### Windows
```powershell
# Using Chocolatey
choco install openjdk --version=21

# Or download from Oracle
# https://www.oracle.com/java/technologies/downloads/
```

#### macOS
```bash
# Using Homebrew
brew install openjdk@21

# Set JAVA_HOME
echo 'export JAVA_HOME=/usr/local/opt/openjdk@21' >> ~/.zshrc
```

#### Linux (Ubuntu/Debian)
```bash
sudo apt update
sudo apt install openjdk-21-jdk
```

### Step 2: Install MongoDB

#### Windows
```powershell
# Using Chocolatey
choco install mongodb

# Or download from MongoDB
# https://www.mongodb.com/try/download/community
```

#### macOS
```bash
# Using Homebrew
brew tap mongodb/brew
brew install mongodb-community
brew services start mongodb/brew
```

#### Linux (Ubuntu/Debian)
```bash
# Import MongoDB public key
wget -qO - https://www.mongodb.org/static/pgp/server-6.0.asc | sudo apt-key add -

# Add MongoDB repository
echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu focal/mongodb-org/6.0 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-6.0.list

# Install MongoDB
sudo apt-get update
sudo apt-get install -y mongodb-org

# Start MongoDB service
sudo systemctl start mongod
sudo systemctl enable mongod
```

### Step 3: Install Maven

#### Windows
```powershell
# Using Chocolatey
choco install maven
```

#### macOS
```bash
# Using Homebrew
brew install maven
```

#### Linux (Ubuntu/Debian)
```bash
sudo apt update
sudo apt install maven
```

### Step 4: Clone and Build the Application

```bash
# Clone the repository
git clone <repository-url>
cd ChitChat

# Build the application
mvn clean compile

# Run tests
mvn test

# Start the application
mvn spring-boot:run
```

### Step 5: Verify Installation

Open your browser and navigate to:
- **Application**: `http://localhost:8080`
- **Health Check**: `http://localhost:8080/actuator/health`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`

## 🏭 Production Deployment

### Option 1: Docker Deployment

#### Create Dockerfile
```dockerfile
FROM openjdk:21-jdk-slim

WORKDIR /app
COPY target/ChitChat-1.0.0.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
```

#### Build and Run Docker Container
```bash
# Build the application
mvn clean package

# Build Docker image
docker build -t chitchat .

# Run Docker container
docker run -d \
  --name chitchat \
  -p 8080:8080 \
  -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/chitchat \
  chitchat
```

### Option 2: Traditional Server Deployment

#### Build for Production
```bash
# Build the application
mvn clean package -DskipTests

# The JAR file will be in target/ChitChat-1.0.0.jar
```

#### Create Systemd Service (Linux)
```bash
# Create service file
sudo nano /etc/systemd/system/chitchat.service
```

```ini
[Unit]
Description=ChitChat Application
After=network.target

[Service]
Type=simple
User=chitchat
WorkingDirectory=/opt/chitchat
ExecStart=/usr/bin/java -jar /opt/chitchat/ChitChat-1.0.0.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
# Enable and start service
sudo systemctl enable chitchat
sudo systemctl start chitchat
```

### Option 3: Cloud Deployment

#### AWS EC2
```bash
# Launch EC2 instance with Ubuntu 20.04
# Install Java, MongoDB, and Maven as shown above
# Deploy the application JAR file
# Configure security groups to allow port 8080
```

#### Google Cloud Platform
```bash
# Create Compute Engine instance
# gcloud compute instances create chitchat-server --machine-type=e2-medium
# Follow the same setup as traditional deployment
```

## 🗄️ Database Configuration

### MongoDB Setup

#### Create Database and User
```javascript
// Connect to MongoDB
mongo

// Create database and user
use chitchat
db.createUser({
  user: "chitchat_user",
  pwd: "secure_password",
  roles: [
    { role: "readWrite", db: "chitchat" }
  ]
})

// Create collections (optional - will be created automatically)
db.createCollection("users")
db.createCollection("conversations")
db.createCollection("messages")
db.createCollection("attachments")
```

#### Connection String Configuration
```properties
# Development
spring.data.mongodb.uri=mongodb://localhost:27017/chitchat

# Production with authentication
spring.data.mongodb.uri=mongodb://chitchat_user:secure_password@localhost:27017/chitchat

# Production with replica set
spring.data.mongodb.uri=mongodb://user:pass@mongo1:27017,mongo2:27017,mongo3:27017/chitchat?replicaSet=rs0
```

## 🔐 Security Setup

### JWT Configuration
```properties
# Generate a strong secret key (at least 256 bits)
jwt.secret=your-super-secret-jwt-key-here-must-be-at-least-256-bits-long
jwt.expiration=86400000  # 24 hours in milliseconds
jwt.refresh-expiration=604800000  # 7 days in milliseconds
```

### Generate JWT Secret
```bash
# Generate secure random key
openssl rand -base64 32

# Or use Java
java -cp target/ChitChat-1.0.0.jar com.learnerview.chitchat.util.KeyGenerator
```

### CORS Configuration
```properties
# Allow specific origins in production
cors.allowed-origins=https://yourdomain.com,https://app.yourdomain.com
cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
cors.allowed-headers=*
cors.allow-credentials=true
```

## 🌍 Environment Configuration

### Development Environment
```properties
# application-dev.properties
spring.profiles.active=dev
logging.level.com.learnerview.chitchat=DEBUG
spring.data.mongodb.uri=mongodb://localhost:27017/chitchat_dev
```

### Production Environment
```properties
# application-prod.properties
spring.profiles.active=prod
logging.level.com.learnerview.chitchat=WARN
logging.file.name=/var/log/chitchat/application.log
spring.data.mongodb.uri=mongodb://user:pass@mongodb-server:27017/chitchat_prod
server.port=8080
```

### Environment Variables
```bash
# Set environment variables
export SPRING_PROFILES_ACTIVE=prod
export MONGODB_URI=mongodb://user:pass@localhost:27017/chitchat
export JWT_SECRET=your-secret-key
export SERVER_PORT=8080
```

## 🔧 File Upload Configuration

### Directory Setup
```bash
# Create upload directory
sudo mkdir -p /opt/chitchat/uploads
sudo chown chitchat:chitchat /opt/chitchat/uploads
sudo chmod 755 /opt/chitchat/uploads
```

### Configuration
```properties
# File upload settings
app.upload.dir=/opt/chitchat/uploads
app.upload.max-file-size=50MB
app.upload.allowed-types=image/jpeg,image/png,image/gif,application/pdf,text/plain
```

## 📊 Monitoring Setup

### Actuator Configuration
```properties
# Enable all actuator endpoints
management.endpoints.web.exposure.include=*
management.endpoint.health.show-details=always
management.info.env.enabled=true
```

### Logback Configuration
```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/log/chitchat/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>/var/log/chitchat/application.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

## 🚨 Troubleshooting

### Common Issues

#### 1. Port Already in Use
```bash
# Find process using port 8080
netstat -tulpn | grep :8080

# Kill the process
sudo kill -9 <PID>
```

#### 2. MongoDB Connection Failed
```bash
# Check MongoDB status
sudo systemctl status mongod

# Check MongoDB logs
sudo tail -f /var/log/mongodb/mongod.log

# Restart MongoDB
sudo systemctl restart mongod
```

#### 3. Memory Issues
```bash
# Increase JVM heap size
java -Xmx2g -Xms1g -jar ChitChat-1.0.0.jar
```

#### 4. File Upload Issues
```bash
# Check directory permissions
ls -la /opt/chitchat/uploads

# Fix permissions
sudo chown -R chitchat:chitchat /opt/chitchat/uploads
sudo chmod -R 755 /opt/chitchat/uploads
```

### Health Check Commands
```bash
# Check application health
curl http://localhost:8080/actuator/health

# Check application info
curl http://localhost:8080/actuator/info

# Check metrics
curl http://localhost:8080/actuator/metrics
```

### Log Analysis
```bash
# View application logs
tail -f /var/log/chitchat/application.log

# Search for errors
grep -i error /var/log/chitchat/application.log

# Monitor real-time logs
tail -f /var/log/chitchat/application.log | grep -i error
```

## 📞 Support

For additional support:
- Check the [GitHub Issues](https://github.com/your-repo/chitchat/issues)
- Review the [API Documentation](API_DOCUMENTATION.md)
- Consult the [README.md](README.md)

---

**Happy Chatting! 🎉**
