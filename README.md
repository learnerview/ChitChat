# ChitChat

ChitChat is a headless real-time communication backend designed to be embedded into products that need chat capabilities without building the communication core from scratch.

This repository represents the current production-oriented implementation state, not a roadmap.

## 1. Why ChitChat Exists

Most products that need chat face the same expensive problems:

1. Secure identity and session handling.
2. Reliable conversation and message persistence.
3. Real-time message fanout to active clients.
4. Authorization rules around who can read, send, edit, and manage communication.
5. A consistent backend contract for web, mobile, and partner integrations.

ChitChat solves these as a focused backend service with REST + WebSocket APIs.

## 2. What Problems It Solves

ChitChat directly addresses:

1. Authentication fragmentation.
	Single JWT auth model for all protected APIs.
2. Conversation lifecycle complexity.
	DMs, groups, participant management, and ownership rules.
3. Message lifecycle requirements.
	Send, list, paginate, edit, and soft-delete with permission controls.
4. Real-time delivery.
	STOMP-based publish-subscribe messaging per conversation.
5. Integration portability.
	UI-neutral API surface that works with any frontend stack.

## 3. Current Scope (Present State)

Included in this implementation:

1. JWT registration and login.
2. User profile read/update, search, account delete, password change.
3. Conversation management:
	DM creation, group creation, rename group, add/remove participants, leave group, **transfer ownership**.
4. Messaging:
	send, history, pagination, edit, soft-delete, **mark as read**.
5. WebSocket real-time publish for conversation messages **with strict Tenant enforcement**.
6. Global API error handling with structured responses.

Intentionally not included:

1. File attachments and media pipeline.
2. Presence and typing indicators.
3. Notification providers (FCM/APNS/email/SMS).
4. Multi-tenant partitioning.
5. Moderation and compliance workflows.

## 4. Architecture Overview

ChitChat uses a monolithic service architecture with clear layer separation.

### 4.1 Layered Design

1. Controllers:
	HTTP and WebSocket entry points.
2. Service interfaces:
	domain contracts.
3. Service implementations:
	business logic and authorization checks.
4. Repositories:
	Mongo persistence contracts.
5. Entities:
	persisted domain model.
6. Security:
	JWT parsing, authentication filter, stateless security config.

### 4.2 SOLID Alignment

1. Single Responsibility:
	Each class focuses on one concern (auth, user, conversation, message, security).
2. Open/Closed:
	New behaviors are added by extending services and controllers without rewriting core contracts.
3. Liskov Substitution:
	Implementations satisfy their service interface contracts.
4. Interface Segregation:
	Service interfaces are domain-specific and focused.
5. Dependency Inversion:
	Controllers depend on service abstractions, not concrete implementations.

## 5. Core Components and Implementation Methods

### 5.1 Auth Domain

Controller:

1. POST /api/auth/register
2. POST /api/auth/login

Key implementation methods:

1. UserService.register(user)
2. AuthenticationManager.authenticate(...)
3. JwtTokenProvider.generateToken(authentication)

How it works:

1. Registration validates and stores a bcrypt password hash.
2. Login authenticates credentials using Spring Security.
3. JWT is generated and returned as bearer token metadata.

### 5.2 User Domain

Controller endpoints:

1. GET /api/users/search
2. GET /api/users/profile
3. GET /api/users/{username}
4. PUT /api/users/profile
5. POST /api/users/password
6. DELETE /api/users/me

Key implementation methods:

1. UserService.findByUsername(username)
2. UserService.searchUsers(query)
3. UserService.updateProfile(username, displayName)
4. UserService.changePassword(username, currentPassword, newPassword)
5. UserService.deleteAccount(username)

Real-world value:

1. Password rotation without re-registration.
2. Lightweight searchable identity directory for composing chats.

### 5.3 Conversation Domain

Controller endpoints:

1. POST /api/conversations/dm?with={username}
2. POST /api/conversations/group?name={groupName}
3. GET /api/conversations
4. GET /api/conversations/{id}
5. PATCH /api/conversations/{id}/name?name={newName}
6. POST /api/conversations/{id}/participants?username={username}
7. DELETE /api/conversations/{id}/participants/{username}
8. POST /api/conversations/{id}/leave
9. POST /api/conversations/{id}/transfer?to={newOwner}

Key implementation methods:

1. ConversationService.createDirectConversation(currentUser, otherUser)
2. ConversationService.createGroupConversation(currentUser, name, members)
3. ConversationService.renameConversation(conversationId, username, newName)
4. ConversationService.addParticipant(conversationId, requester, participant)
5. ConversationService.removeParticipant(conversationId, requester, participant)
6. ConversationService.leaveConversation(conversationId, username)
7. ConversationService.transferOwnership(conversationId, username, newOwner)

Important business rules:

1. Direct conversation with self is blocked.
2. DM is reused if it already exists between two users.
3. Group owner controls rename and membership changes.
4. Owner cannot be removed or leave without ownership transfer.

### 5.4 Message Domain

Controller endpoints:

1. GET /api/messages/{conversationId}
2. GET /api/messages/{conversationId}/paginated?page=0&size=25
3. POST /api/messages/{conversationId}
4. PATCH /api/messages/{messageId}
5. DELETE /api/messages/{messageId}
6. POST /api/messages/{conversationId}/read

Key implementation methods:

1. MessageService.sendMessage(conversationId, sender, content, replyToId)
2. MessageService.getMessageHistory(conversationId, viewer)
3. MessageService.getMessageHistoryPaginated(...)
4. MessageService.editMessage(messageId, editor, updatedContent)
5. MessageService.deleteMessage(messageId, requester)
6. MessageService.markAsRead(conversationId, username)

Important business rules:

1. Only participants can read or send in a conversation.
2. Reply target must exist and belong to same conversation.
3. Only sender can edit a message.
4. Sender or conversation owner can soft-delete a message.
5. Read receipts are tracked securely per user.

## 6. Real-Time Messaging (WebSocket)

Protocol:

1. STOMP over WebSocket with SockJS fallback.

Endpoints:

1. Handshake endpoint: /ws
2. App destination: /app/conversations/{conversationId}/send
3. Topic destination: /topic/conversations/{conversationId}

Flow with strict tenant enforcement:

1. Client connects via STOMP, passing `X-Tenant-Id` in headers.
2. `WebSocketTenantHandshakeInterceptor` validates tenant payload at connection.
3. Client sends message payload to app destination.
4. `RealtimeMessageController` binds authenticated context and native STOMP headers.
5. MessageService persists the message.
6. SimpMessagingTemplate broadcasts saved message to conversation topic.

## 7. Data Model

MongoDB collections:

1. users
2. conversations
3. messages

### 7.1 User

Primary fields:

1. id
2. username
3. displayName
4. password (bcrypt hash)
5. createdAt

### 7.2 Conversation

Primary fields:

1. id
2. type (DM, GROUP)
3. name
4. createdBy
5. createdAt
6. participants (set of usernames)

### 7.3 Message

Primary fields:

1. id
2. conversationId
3. sender
4. content
5. replyToId
6. createdAt
7. edited
8. deleted
9. updatedAt

## 8. Security Model

Authentication:

1. JWT bearer token validation in JwtAuthenticationFilter.
2. Stateless session policy.

Authorization:

1. /api/auth/** and /ws/** are public entry points.
2. All other APIs require authenticated identity.
3. Domain-level checks enforce participant and owner rules.

Bearer header format:

```http
Authorization: Bearer <jwt-token>
```

## 9. Error Handling Contract

GlobalExceptionHandler provides consistent response shape:

1. timestamp
2. status
3. error
4. message

Handled classes include:

1. ResponseStatusException
2. MethodArgumentNotValidException
3. ConstraintViolationException
4. Generic Exception fallback

## 10. API Integration Guidelines

Required request headers for all API calls:

1. Authorization: Bearer <token>
2. X-Tenant-Id: <tenant-id>

### 10.1 Recommended Client Integration Order

1. Register user.
2. Login and store token securely.
3. Load user profile.
4. Discover users via search.
5. Create or open DM/group.
6. Fetch initial history with paginated API.
7. Connect WebSocket and subscribe to conversation topic.
8. Send via REST or WebSocket app destination.

### 10.2 Token Handling Guidelines

1. Keep access token in secure storage.
2. Send Authorization header on every protected REST call.
3. Reconnect WebSocket after auth refresh events in client.

### 10.3 Pagination Guidelines

1. Use newest-first pagination endpoint for long conversations.
2. Keep page size in 20-50 range for responsive UX.
3. Merge incoming real-time messages into cached page state.

### 10.4 Conversation Ownership Guidelines

1. Show owner-only controls in UI for rename/member operations.
2. Prevent owner self-leave in UI and explain transfer requirement.

### 10.5 Message Editing and Deletion Guidelines

1. Expose edit action only for sender-authored messages.
2. Expose delete action for sender and group owner.
3. Render deleted messages as placeholders to preserve thread continuity.

## 11. Real-World Use Cases

### Use Case 1: Team Collaboration Chat

Problem:
Teams need project channels and private collaboration without building a custom chat backend.

How ChitChat solves it:

1. Create groups per project.
2. Add/remove participants as teams change.
3. Use real-time topic subscriptions for active room updates.
4. Keep history and moderation-ready metadata (edited/deleted flags).

### Use Case 2: Marketplace Buyer-Seller Messaging

Problem:
Users need reliable direct conversations with minimal backend complexity.

How ChitChat solves it:

1. Open deterministic DMs between buyer and seller.
2. Persist conversation and message records in Mongo.
3. Deliver live messages through topic subscription.

### Use Case 3: In-App Support Channel

Problem:
Product support teams require controlled group spaces with message governance.

How ChitChat solves it:

1. Support lead is group owner.
2. Owner controls participant access.
3. Support staff can manage message lifecycle with edit/delete rules.

## 12. Build and Run

Prerequisites:

1. Java 17+
2. MongoDB 4.4+
3. Maven 3.6+

Run locally:

```bash
mvn spring-boot:run
```

Compile check:

```bash
mvn -DskipTests compile
```

Default properties:

1. Mongo URI: mongodb://localhost:27017/chitchat
2. Port: 8080
3. JWT expiration: 86400000 ms

## 13. Key Project Structure

```text
src/main/java/com/learnerview/chitchat/
  ChitChatApplication.java
  config/
	 SecurityConfig.java
	 WebSocketConfig.java
  controllers/
	 AuthController.java
	 ConversationController.java
	 MessageController.java
	 RealtimeMessageController.java
	 UserController.java
  dto/
	 AuthResponse.java
	 LoginRequest.java
	 RegisterRequest.java
	 UserProfileResponse.java
  entities/
	 Conversation.java
	 ConversationType.java
	 Message.java
	 User.java
  exception/
	 GlobalExceptionHandler.java
  repositories/
	 ConversationRepository.java
	 MessageRepository.java
	 UserRepository.java
  security/
	 JwtAuthenticationFilter.java
	 JwtTokenProvider.java
  service/
	 ConversationService.java
	 MessageService.java
	 UserService.java
  service/impl/
	 ConversationServiceImpl.java
	 MessageServiceImpl.java
	 MongoUserDetailsService.java
	 UserServiceImpl.java
```

## 14. Integration Checklist

Before production integration, verify:

1. JWT secret is rotated to a long random value.
2. CORS and origin policies are set for your client domains.
3. MongoDB backup policy exists.
4. API gateway timeout/retry strategy is configured.
5. WebSocket reconnect strategy is implemented in clients.
6. Monitoring and alerting are attached to authentication and message endpoints.

## 16. Integration Modes

ChitChat supports three integration modes.

### 16.1 Standalone Mode

1. Uses built-in registration and login.
2. Internal JWT is issued by ChitChat.
3. Best for independent products with direct user management.

### 16.2 Integrated Mode

1. Accepts external JWT tokens.
2. Treats ChitChat as an identity consumer, not only identity owner.
3. Can auto-provision users on first valid external identity request.
4. Supports exchanging authenticated external context into internal ChitChat token via /api/auth/exchange.

### 16.3 Event-Driven Mode

1. Emits webhook events to tenant-registered endpoints.
2. Current high-value events:
	 message.sent
	 message.deleted
	 conversation.created
3. Delivery payload shape:

```json
{
	"tenantId": "acme",
	"event": "message.sent",
	"timestamp": "2026-03-28T10:15:30",
	"data": {
		"messageId": "...",
		"conversationId": "..."
	}
}
```

Webhook management endpoints:

1. POST /api/integrations/webhooks
2. GET /api/integrations/webhooks
3. DELETE /api/integrations/webhooks/{id}

Webhook security header:

1. X-Signature: HMAC_SHA256(payload, secret)

## 17. Architecture Diagrams

### 17.1 Message Flow

```text
Client
	-> REST / WebSocket
	-> Controller
	-> Service
	-> MongoDB
	-> WebSocket Topic Fanout
	-> Webhook Event Delivery
```

### 17.2 External Auth Flow

```text
Client (External JWT)
	-> JwtAuthenticationFilter
	-> External Claim Parse/Validate
	-> Tenant-Aware User Resolve (or Auto-Provision)
	-> Service Access
```

## 18. License

MIT. See [LICENSE](LICENSE).
