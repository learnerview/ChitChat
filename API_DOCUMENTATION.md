# 📚 ChitChat API Documentation

Complete REST API reference for the ChitChat real-time chat application. All endpoints are documented with request/response examples, authentication requirements, and error handling.

## 🔐 Authentication

All API endpoints (except `/api/auth/**` and public endpoints) require JWT authentication.

### Authorization Header
```
Authorization: Bearer <jwt_token>
```

### JWT Token Structure
```json
{
  "sub": "username",
  "iat": 1640995200,
  "exp": 1641081600
}
```

---

## 📋 API Endpoints

### Authentication Endpoints

#### Register User
```http
POST /api/auth/register
Content-Type: application/json
```

**Request Body:**
```json
{
  "username": "john_doe",
  "password": "SecurePass123!",
  "displayName": "John Doe",
  "bio": "Software Developer"
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "User registered successfully",
  "data": {
    "id": "64a1b2c3d4e5f6789012345",
    "username": "john_doe",
    "displayName": "John Doe",
    "bio": "Software Developer",
    "createdAt": "2024-01-15T10:30:00Z"
  }
}
```

#### User Login
```http
POST /api/auth/login
Content-Type: application/json
```

**Request Body:**
```json
{
  "username": "john_doe",
  "password": "SecurePass123!"
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJqb2huX2RvZSIsImlhdCI6MTY0MDk5NTIwMCwiZXhwIjoxNjQxMDgxNjAwfQ.signature",
    "type": "Bearer",
    "username": "john_doe",
    "displayName": "John Doe"
  }
}
```

#### Refresh Token
```http
POST /api/auth/refresh
Authorization: Bearer <refresh_token>
```

**Response (200):**
```json
{
  "status": 200,
  "data": {
    "token": "new_jwt_token_here",
    "type": "Bearer"
  }
}
```

---

### User Management Endpoints

#### Get User Profile
```http
GET /api/users/profile
Authorization: Bearer <jwt_token>
```

**Response (200):**
```json
{
  "status": 200,
  "data": {
    "id": "64a1b2c3d4e5f6789012345",
    "username": "john_doe",
    "displayName": "John Doe",
    "bio": "Software Developer",
    "avatarUrl": "https://example.com/avatar.jpg",
    "online": true,
    "lastSeen": "2024-01-15T10:25:00Z",
    "createdAt": "2024-01-10T08:00:00Z"
  }
}
```

#### Update User Profile
```http
POST /api/users/profile
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "displayName": "John Smith",
  "bio": "Senior Software Engineer",
  "avatarUrl": "https://example.com/new-avatar.jpg"
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Profile updated successfully",
  "data": {
    "id": "64a1b2c3d4e5f6789012345",
    "username": "john_doe",
    "displayName": "John Smith",
    "bio": "Senior Software Engineer",
    "avatarUrl": "https://example.com/new-avatar.jpg"
  }
}
```

#### Search Users
```http
GET /api/users/search?query=john&limit=10
Authorization: Bearer <jwt_token>
```

**Response (200):**
```json
{
  "status": 200,
  "data": [
    {
      "id": "64a1b2c3d4e5f6789012345",
      "username": "john_doe",
      "displayName": "John Doe",
      "avatarUrl": "https://example.com/avatar.jpg",
      "online": true
    },
    {
      "id": "64a1b2c3d4e5f6789012346",
      "username": "john_smith",
      "displayName": "John Smith",
      "avatarUrl": "https://example.com/avatar2.jpg",
      "online": false
    }
  ]
}
```

#### Get User by Username
```http
GET /api/users/{username}
Authorization: Bearer <jwt_token>
```

**Response (200):**
```json
{
  "status": 200,
  "data": {
    "id": "64a1b2c3d4e5f6789012345",
    "username": "john_doe",
    "displayName": "John Doe",
    "bio": "Software Developer",
    "avatarUrl": "https://example.com/avatar.jpg",
    "online": true,
    "lastSeen": "2024-01-15T10:25:00Z"
  }
}
```

---

### Conversation Endpoints

#### Get My Conversations
```http
GET /api/conversations/my
Authorization: Bearer <jwt_token>
```

**Response (200):**
```json
{
  "status": 200,
  "data": [
    {
      "id": "64a1b2c3d4e5f6789012347",
      "type": "DM",
      "name": null,
      "users": ["john_doe", "jane_doe"],
      "memberCount": 2,
      "createdAt": "2024-01-15T09:00:00Z",
      "lastMessage": {
        "id": "64a1b2c3d4e5f6789012348",
        "content": "Hey there!",
        "sender": "jane_doe",
        "timeStamp": "2024-01-15T10:15:00Z"
      }
    },
    {
      "id": "64a1b2c3d4e5f6789012349",
      "type": "GROUP",
      "name": "Development Team",
      "users": ["john_doe", "jane_doe", "bob_smith"],
      "memberCount": 3,
      "createdAt": "2024-01-14T14:00:00Z",
      "lastMessage": {
        "id": "64a1b2c3d4e5f6789012350",
        "content": "Meeting at 3pm",
        "sender": "bob_smith",
        "timeStamp": "2024-01-15T09:30:00Z"
      }
    }
  ]
}
```

#### Create Direct Message
```http
POST /api/conversations/dm?withUser=jane_doe
Authorization: Bearer <jwt_token>
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Direct message created",
  "data": {
    "id": "64a1b2c3d4e5f6789012351",
    "type": "DM",
    "users": ["john_doe", "jane_doe"],
    "memberCount": 2,
    "createdAt": "2024-01-15T10:30:00Z"
  }
}
```

#### Create Group Conversation
```http
POST /api/conversations/group
Authorization: Bearer <jwt_token>
Content-Type: application/x-www-form-urlencoded
```

**Request Body:**
```
name=Development Team&isPublic=false&description=Team for development discussions
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Group conversation created",
  "data": {
    "id": "64a1b2c3d4e5f6789012352",
    "type": "GROUP",
    "name": "Development Team",
    "description": "Team for development discussions",
    "isPublic": false,
    "ownerId": "64a1b2c3d4e5f6789012345",
    "memberCount": 1,
    "createdAt": "2024-01-15T10:35:00Z"
  }
}
```

#### Get Conversation Details
```http
GET /api/conversations/{conversationId}
Authorization: Bearer <jwt_token>
```

**Response (200):**
```json
{
  "status": 200,
  "data": {
    "id": "64a1b2c3d4e5f6789012351",
    "type": "DM",
    "users": ["john_doe", "jane_doe"],
    "memberCount": 2,
    "createdAt": "2024-01-15T10:30:00Z",
    "settings": {
      "adminOnlyMessaging": false
    }
  }
}
```

---

### Message Endpoints

#### Get Message History
```http
GET /api/messages/{conversationId}
Authorization: Bearer <jwt_token>
```

**Response (200):**
```json
{
  "status": 200,
  "data": [
    {
      "id": "64a1b2c3d4e5f6789012353",
      "conversationId": "64a1b2c3d4e5f6789012351",
      "sender": "jane_doe",
      "content": "Hey there!",
      "timeStamp": "2024-01-15T10:15:00Z",
      "edited": false,
      "deleted": false,
      "replyToId": null,
      "attachments": []
    },
    {
      "id": "64a1b2c3d4e5f6789012354",
      "conversationId": "64a1b2c3d4e5f6789012351",
      "sender": "john_doe",
      "content": "Hi Jane! How are you?",
      "timeStamp": "2024-01-15T10:16:00Z",
      "edited": false,
      "deleted": false,
      "replyToId": "64a1b2c3d4e5f6789012353",
      "attachments": []
    }
  ]
}
```

#### Send Message
```http
POST /api/messages/{conversationId}
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "content": "Hello everyone!",
  "replyToId": "64a1b2c3d4e5f6789012353",
  "attachments": ["64a1b2c3d4e5f6789012355"]
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Message sent successfully",
  "data": {
    "id": "64a1b2c3d4e5f6789012356",
    "conversationId": "64a1b2c3d4e5f6789012351",
    "sender": "john_doe",
    "content": "Hello everyone!",
    "timeStamp": "2024-01-15T10:20:00Z",
    "edited": false,
    "deleted": false,
    "replyToId": "64a1b2c3d4e5f6789012353",
    "attachments": ["64a1b2c3d4e5f6789012355"]
  }
}
```

#### Edit Message
```http
PUT /api/messages/{messageId}/edit
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "content": "Updated message content"
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Message updated successfully",
  "data": {
    "id": "64a1b2c3d4e5f6789012356",
    "content": "Updated message content",
    "edited": true,
    "editedAt": "2024-01-15T10:25:00Z"
  }
}
```

#### Delete Message
```http
DELETE /api/messages/{messageId}?deleteForEveryone=true
Authorization: Bearer <jwt_token>
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Message deleted successfully"
}
```

#### Forward Message
```http
POST /api/messages/{messageId}/forward
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "toConversationId": "64a1b2c3d4e5f6789012357"
}
```

**Response (201):**
```json
{
  "status": 201,
  "message": "Message forwarded successfully",
  "data": {
    "id": "64a1b2c3d4e5f6789012358",
    "conversationId": "64a1b2c3d4e5f6789012357",
    "sender": "john_doe",
    "content": "Hello everyone!",
    "forwarded": true,
    "originalMessageId": "64a1b2c3d4e5f6789012356",
    "timeStamp": "2024-01-15T10:30:00Z"
  }
}
```

#### Mark Messages as Read
```http
POST /api/messages/{conversationId}/read
Authorization: Bearer <jwt_token>
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Messages marked as read"
}
```

---

### File Upload Endpoints

#### Upload File
```http
POST /api/files/upload
Authorization: Bearer <jwt_token>
Content-Type: multipart/form-data
```

**Request Body:**
```
file: <binary_file_data>
type: IMAGE|FILE|DOCUMENT
```

**Response (200):**
```json
{
  "status": 200,
  "message": "File uploaded successfully",
  "data": {
    "id": "64a1b2c3d4e5f6789012359",
    "filename": "document.pdf",
    "originalFilename": "my-document.pdf",
    "url": "http://localhost:8080/api/files/download/64a1b2c3d4e5f6789012359",
    "type": "DOCUMENT",
    "size": 1024000,
    "mimeType": "application/pdf",
    "uploadedBy": "john_doe",
    "uploadedAt": "2024-01-15T10:35:00Z"
  }
}
```

#### Download File
```http
GET /api/files/download/{fileId}
Authorization: Bearer <jwt_token>
```

**Response (200):**
```
Binary file data with appropriate Content-Type header
```

#### Get File Info
```http
GET /api/files/info/{fileId}
Authorization: Bearer <jwt_token>
```

**Response (200):**
```json
{
  "status": 200,
  "data": {
    "id": "64a1b2c3d4e5f6789012359",
    "filename": "document.pdf",
    "type": "DOCUMENT",
    "size": 1024000,
    "mimeType": "application/pdf",
    "uploadedBy": "john_doe",
    "uploadedAt": "2024-01-15T10:35:00Z"
  }
}
```

#### Delete File
```http
DELETE /api/files/{fileId}
Authorization: Bearer <jwt_token>
```

**Response (200):**
```json
{
  "status": 200,
  "message": "File deleted successfully"
}
```

---

### Presence Endpoints

#### Get Online Users
```http
GET /api/presence/online
Authorization: Bearer <jwt_token>
```

**Response (200):**
```json
{
  "status": 200,
  "data": [
    {
      "username": "jane_doe",
      "displayName": "Jane Doe",
      "status": "online",
      "lastSeen": "2024-01-15T10:40:00Z"
    },
    {
      "username": "bob_smith",
      "displayName": "Bob Smith",
      "status": "online",
      "lastSeen": "2024-01-15T10:38:00Z"
    }
  ]
}
```

#### Send Typing Indicator
```http
POST /api/presence/typing
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "conversationId": "64a1b2c3d4e5f6789012351",
  "typing": true
}
```

**Response (200):**
```json
{
  "status": 200,
  "message": "Typing indicator sent"
}
```

---

## 🚨 Error Responses

### Standard Error Format
```json
{
  "status": error_code,
  "message": "Human readable error message",
  "timestamp": "2024-01-15T10:45:00Z",
  "path": "/api/endpoint"
}
```

### Common Error Codes

| Status Code | Error Code | Description |
|-------------|------------|-------------|
| 400 | BAD_REQUEST | Invalid request data |
| 401 | UNAUTHORIZED | Missing or invalid authentication |
| 403 | FORBIDDEN | Insufficient permissions |
| 404 | NOT_FOUND | Resource not found |
| 409 | CONFLICT | Resource conflict |
| 429 | TOO_MANY_REQUESTS | Rate limit exceeded |
| 500 | INTERNAL_SERVER_ERROR | Server error |

### Example Error Responses

#### Validation Error (400)
```json
{
  "status": 400,
  "message": "Validation failed",
  "errors": [
    {
      "field": "username",
      "message": "Username must be between 3 and 20 characters"
    },
    {
      "field": "password",
      "message": "Password must be at least 6 characters"
    }
  ]
}
```

#### Authentication Error (401)
```json
{
  "status": 401,
  "message": "Invalid or expired token",
  "timestamp": "2024-01-15T10:45:00Z"
}
```

#### Not Found Error (404)
```json
{
  "status": 404,
  "message": "User not found",
  "timestamp": "2024-01-15T10:45:00Z"
}
```

#### Rate Limit Error (429)
```json
{
  "status": 429,
  "message": "Too many requests. Please try again later.",
  "retryAfter": 60
}
```

---

## 🔄 WebSocket API

### Connection
```
ws://localhost:8080/ws
```

### Authentication
```
Authorization: Bearer <jwt_token>
```

### Subscriptions

#### Join Conversation
```
SUBSCRIBE /topic/chat/{conversationId}
```

#### User Notifications
```
SUBSCRIBE /topic/notifications/{username}
```

#### Typing Indicators
```
SUBSCRIBE /topic/typing/{conversationId}
```

### Message Formats

#### New Message
```json
{
  "type": "MESSAGE",
  "payload": {
    "id": "64a1b2c3d4e5f6789012356",
    "conversationId": "64a1b2c3d4e5f6789012351",
    "sender": "john_doe",
    "content": "Hello everyone!",
    "timeStamp": "2024-01-15T10:20:00Z"
  }
}
```

#### Typing Indicator
```json
{
  "type": "TYPING",
  "payload": {
    "conversationId": "64a1b2c3d4e5f6789012351",
    "username": "john_doe",
    "typing": true
  }
}
```

#### User Online Status
```json
{
  "type": "PRESENCE",
  "payload": {
    "username": "jane_doe",
    "online": true,
    "lastSeen": "2024-01-15T10:40:00Z"
  }
}
```

---

## 📊 Rate Limiting

### Limits
- **Authentication endpoints**: 5 requests per minute
- **Message endpoints**: 60 requests per minute
- **File upload**: 10 requests per minute
- **Other endpoints**: 100 requests per minute

### Headers
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1640995800
```

---

## 🔍 Testing

### Using curl
```bash
# Health check
curl http://localhost:8080/actuator/health

# User registration
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"SecurePass123!","displayName":"Test User"}'

# User login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"SecurePass123!"}'
```

### Using Swagger UI
Visit `http://localhost:8080/swagger-ui.html` for interactive API testing.

---

**API Version**: 1.0.0  
**Last Updated**: 2024-01-15  
**Base URL**: `http://localhost:8080/api`
