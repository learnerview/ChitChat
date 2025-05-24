# 🗨️ ChitChat - Real-Time Chat Application

A modern, feature-rich chat application built with Spring Boot 3.2 and MongoDB, supporting real-time messaging, file sharing, and comprehensive user management.

## ✨ Key Features

- **🔐 Secure Authentication** - JWT-based authentication with refresh tokens
- **💬 Real-Time Messaging** - WebSocket-powered instant messaging with typing indicators
- **👥 Multi-User Support** - Direct messages, group chats, and public conversations
- **📁 File Sharing** - Secure file upload/download with metadata management
- **🔔 Smart Notifications** - Push notifications and real-time alerts
- **👤 User Profiles** - Customizable profiles with avatars and status
- **🛡️ Content Moderation** - Automated profanity filtering and content validation
- **📊 Presence Management** - Online status, typing indicators, and read receipts
- **🔍 Search & Discovery** - User search and conversation discovery
- **📈 Rate Limiting** - API protection against abuse
- **📚 API Documentation** - Complete OpenAPI 3.0 specification with Swagger UI

## 🚀 Quick Start

### Prerequisites
- Java 17 or higher
- MongoDB 4.4 or higher
- Maven 3.6 or higher

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd ChitChat
   ```

2. **Configure MongoDB**
   ```bash
   # Start MongoDB service
   mongod
   ```

3. **Configure application**
   ```properties
   # src/main/resources/application.properties
   spring.data.mongodb.uri=mongodb://localhost:27017/chitchat
   jwt.secret=your-secret-key-here
   ```

4. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

5. **Access the application**
   - API Base URL: `http://localhost:8080`
   - Swagger UI: `http://localhost:8080/swagger-ui.html`
   - Health Check: `http://localhost:8080/actuator/health`

## 🏗️ Architecture

### Technology Stack
- **Backend**: Spring Boot 3.2, Spring Security, Spring WebSocket
- **Database**: MongoDB with Spring Data
- **Authentication**: JWT with refresh tokens
- **Real-Time**: WebSocket with STOMP messaging
- **Documentation**: OpenAPI 3.0 with Swagger UI
- **Monitoring**: Spring Boot Actuator

### Core Components
- **Authentication Module** - JWT-based secure authentication
- **Messaging Engine** - Real-time message delivery via WebSocket
- **User Management** - Profile management and user search
- **Conversation System** - DMs, groups, and public chats
- **File Service** - Secure file upload and management
- **Notification Service** - Push notifications and alerts
- **Moderation System** - Content filtering and validation

## 📱 Usage Examples

### User Registration & Login
```bash
# Register new user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"SecurePass123!","displayName":"John Doe"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"SecurePass123!"}'
```

### Sending Messages
```bash
# Send message to conversation
curl -X POST http://localhost:8080/api/messages/{conversationId} \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"content":"Hello World!","replyToId":null,"attachments":[]}'
```

### File Upload
```bash
# Upload file
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer {token}" \
  -F "file=@document.pdf" \
  -F "type=FILE"
```

## 🔧 Configuration

### Environment Variables
```properties
# Database Configuration
spring.data.mongodb.uri=mongodb://localhost:27017/chitchat

# JWT Configuration
jwt.secret=your-super-secret-key-here
jwt.expiration=86400000

# File Upload Configuration
app.upload.dir=./uploads
app.upload.max-file-size=50MB

# Rate Limiting
app.rate-limit.requests-per-minute=100
```

### Security Configuration
- JWT tokens with configurable expiration
- CORS configuration for cross-origin requests
- Rate limiting on all endpoints
- Input validation and sanitization

## 📊 Monitoring & Health

### Actuator Endpoints
- **Health Check**: `/actuator/health`
- **Application Info**: `/actuator/info`
- **Metrics**: `/actuator/metrics`
- **Environment**: `/actuator/env`

### API Documentation
- **Swagger UI**: `/swagger-ui.html`
- **OpenAPI Spec**: `/v3/api-docs`

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

For support and questions:
- Create an issue in the repository
- Check the [API Documentation](API_DOCUMENTATION.md)
- Review the [Setup Guide](SETUP_GUIDE.md)

---

**Built with ❤️ using Spring Boot 3.2 and MongoDB**
