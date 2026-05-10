# ChitChat

ChitChat is a chat backend built with Spring Boot and MongoDB.

It handles authentication, conversations, messaging, and real-time communication through REST APIs and WebSocket connections. The service is frontend-independent, so it can be used with web, mobile, or desktop clients.

## Why ChitChat

Most products that add chat face the same backend problems:

* Secure identity and session handling
* Reliable conversation and message persistence
* Real-time message fanout to active clients
* Authorization rules around who can read, send, edit, and manage conversations
* A consistent API contract for multiple client platforms

ChitChat solves these as a focused, multi-tenant communication backend.

## Features

### Authentication

* JWT-based authentication
* User registration and login
* Stateless security configuration

### User Management

* Profile fetch and update
* Password change
* Account deletion
* User search

### Conversations

* Direct conversations
* Group conversations
* Participant management
* Group rename
* Ownership transfer
* Leave conversation flow

### Messaging

* Send messages
* Paginated history
* Message replies
* Edit messages
* Soft delete messages
* Read tracking

### Real-Time Messaging

* STOMP over WebSocket
* SockJS fallback support
* Conversation-based subscriptions
* Live message fanout

---

# Tech Stack

* Java 17
* Spring Boot
* Spring Security
* Spring WebSocket
* MongoDB
* Maven
* JWT
* STOMP

---

# Architecture

ChitChat follows a layered monolith structure.

## Why Monolith

A monolithic design keeps authentication, conversations, and messaging tightly coupled with shared data access and consistent tenant isolation. This avoids operational complexity of distributed systems while maintaining clear separation of concerns.

## Layers

### Controllers

REST and WebSocket entry points. Request validation and extracting authenticated context.

### Services

Business logic and authorization rules. Services implement domain-specific contracts and enforce permission checks before data modifications.

### Repositories

MongoDB persistence layer. All business data (conversations, messages) are scoped by `tenantId`. Users are global entities that can belong to multiple workspaces via memberships.

### Entities

Domain models stored in MongoDB. Each business entity includes tenant isolation. Users are shared across the system.

### Security

JWT authentication filter validates bearer tokens. A separate `TenantHeaderFilter` ensures each request specifies which workspace context it is operating in.

---

# Authentication APIs

## Register

`POST /api/auth/register`

Creates a new user account. Password is hashed with bcrypt before storage.

Request:
```json
{
  "username": "john",
  "displayName": "John Doe",
  "password": "secure_password"
}
```

## Login

`POST /api/auth/login`

Authenticates the user and returns a JWT token that includes both username and tenant ID.

Request:
```json
{
  "username": "john",
  "password": "secure_password"
}
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "username": "john",
  "displayName": "John Doe",
  "currentTenantId": "60d5ec49a1b2c3e4f5g6h7i8",
  "currentTenantName": "Acme Corp",
  "tenants": [
    {
      "id": "60d5ec49a1b2c3e4f5g6h7i8",
      "name": "Acme Corp",
      "slug": "acme-corp",
      "role": "OWNER"
    }
  ]
}
```

---

# Workspace (Tenant) APIs

Workspaces allow teams to isolate their conversations and messages. Users must be members of a workspace to access its content.

## Create Workspace

`POST /api/workspaces`

Creates a new workspace. The authenticated user becomes the `OWNER`.

Request:
```json
{
  "name": "Acme Corp",
  "slug": "acme-corp",
  "description": "Primary workspace for Acme"
}
```

## List My Workspaces

`GET /api/workspaces`

Returns all workspaces where the authenticated user is a member.

## Get Workspace Details

`GET /api/workspaces/{tenantId}`

## Update Workspace

`PUT /api/workspaces/{tenantId}`

Only owners can update.

## Delete Workspace

`DELETE /api/workspaces/{tenantId}`

Only owners can delete.

---

# Invite APIs

Invite links allow users to join workspaces.

## Generate Invite Link

`POST /api/invites/generate`

Generates a unique token for a workspace. Only owners/admins can generate.

Request:
```json
{
  "tenantId": "acme-corp",
  "expiresInDays": "7"
}
```

## Accept Invite

`POST /api/invites/accept`

Adds the authenticated user to the workspace associated with the token.

Request:
```json
{
  "token": "uuid-token-here"
}
```

---

# Conversation APIs

## Create Direct Conversation

`POST /api/conversations/dm?with=jane`

Creates a direct conversation between two users.

If a DM already exists between them, the existing conversation is returned.

Business rules:
* Users cannot create DMs with themselves
* DMs reuse existing conversations if already present

## Create Group Conversation

`POST /api/conversations/group?name=Project%20Team`

Creates a group conversation. The authenticated user becomes the owner.

Request body (optional):
```json
["user1", "user2", "user3"]
```

## Get User Conversations

`GET /api/conversations`

Returns all conversations for the authenticated user (both DMs and groups).

## Get Conversation By ID

`GET /api/conversations/{id}`

Returns conversation details. Only participants can access.

## Rename Group

`PATCH /api/conversations/{id}/name?name=New%20Group%20Name`

Only the group owner can rename the group.

## Add Participant

`POST /api/conversations/{id}/participants?username=newuser`

Adds a participant to a group. Only the group owner can add members.

## Remove Participant

`DELETE /api/conversations/{id}/participants/{username}`

Removes a participant from a group. Only the group owner can remove members.

The owner cannot remove themselves; ownership must be transferred first.

## Leave Conversation

`POST /api/conversations/{id}/leave`

Allows a participant to leave a conversation.

Business rules:
* Group owners must transfer ownership before leaving
* Leaving a DM is allowed for any participant

## Transfer Ownership

`POST /api/conversations/{id}/transfer?to=newowner`

Transfers group ownership to another participant. Only the current owner can transfer.

The new owner must already be a participant.

---

# Message APIs

## Get Message History

`GET /api/messages/{conversationId}`

Returns all messages for a conversation. Only participants can access.

## Search Messages (In Conversation)

`GET /api/messages/{conversationId}/search?query=hello`

Searches for messages containing the query within a specific conversation.

## Search All My Messages (In Current Workspace)

`GET /api/messages/search?query=urgent`

Searches across all conversations the user is a participant of in the **current workspace** (specified by `X-Tenant-Id`).

## Get Paginated Messages

`GET /api/messages/{conversationId}/paginated?page=0&size=25`

Returns paginated messages for a conversation. Only participants can access.

Response:
```json
{
  "content": [...],
  "page": 0,
  "size": 25,
  "totalElements": 250,
  "totalPages": 10,
  "last": false
}
```

## Send Message

`POST /api/messages/{conversationId}`

Creates a new message in the conversation. Only participants can send.

Request:
```json
{
  "content": "Hello everyone",
  "replyToId": "optional-message-id"
}
```

Business rules:
* Only conversation participants can send messages
* Reply targets must belong to the same conversation
* Empty messages are rejected

## Edit Message

`PATCH /api/messages/{messageId}`

Updates message content. Only the original sender can edit.

Request:
```json
{
  "content": "Updated message"
}
```

Business rules:
* Only the sender can edit their own messages
* Deleted messages cannot be edited
* Edited flag is set on the message

## Delete Message

`DELETE /api/messages/{messageId}`

Soft-deletes a message. The original sender or conversation owner can perform this.

Business rules:
* Messages are marked as deleted, not removed from the database
* Message content is replaced with "This message was deleted"
* Reply chains preserve message history

## Mark Messages As Read

`POST /api/messages/{conversationId}/read`

Marks all unread messages in a conversation as read for the authenticated user.

---

# WebSocket

Real-time messaging uses STOMP over WebSocket with SockJS fallback.

## Connection Endpoint

`/ws`

Requires `X-Tenant-Id` header and JWT bearer token in the initial handshake.

## Publish Destination

`/app/conversations/{conversationId}/send`

Send message requests to this destination. Messages are persisted and broadcast to all subscribers.

## Subscribe Destination

`/topic/conversations/{conversationId}`

Subscribe to this topic to receive real-time messages for a conversation.

Only participants of the conversation can subscribe.

## Flow

1. Client connects to `/ws` with `X-Tenant-Id` header and JWT token
2. Connection is rejected if tenant header is missing
3. Client subscribes to `/topic/conversations/{conversationId}`
4. Subscription is rejected if client is not a participant
5. Client sends message to `/app/conversations/{conversationId}/send`
6. MessageService persists the message
7. Message is broadcast to all subscribers of that conversation topic

## Security

WebSocket security is multi-layered:

* Tenant header validation at connection time (rejected if missing)
* JWT bearer token validation (same as REST)
* Subscription authorization checks (conversation membership required)
* Tenant claim validation in JWT (token tenant must match request tenant)

---

# Multi-Tenancy

ChitChat is designed as a shared, multi-tenant backend. All requests must include the tenant ID header:

```http
X-Tenant-Id: acme-corp
```

Tenant isolation is enforced at multiple layers:

* Request filter validates tenant header presence
* JWT token includes tenant claim
* All queries include tenantId in filters
* Repositories implement compound indexes on tenantId + business key

This allows one ChitChat instance to serve multiple independent organizations (tenants) without data leaks.

---

# Security

## Authentication

Protected APIs require JWT bearer token:

```http
Authorization: Bearer <jwt-token>
```

Tokens are signed with HMAC-SHA512 using the JWT_SECRET. Tokens include username and tenantId claims.

## Authorization

* `/api/auth/**` - Public (registration and login)
* `/ws/**` - Public for connection, but tenant header required at handshake
* All other `/api/**` - Requires valid JWT

Domain-level authorization:

* Only conversation participants can send messages or view history
* Only group owners can rename groups or manage participants
* Only message senders can edit their own messages
* Only message senders or conversation owners can delete messages

## Tenant Validation

* Request tenant header is validated and stored in request-scoped ThreadLocal
* JWT token tenant claim is compared with request tenant header
* Mismatch results in 401 Unauthorized
* All repository queries are scoped by tenant, preventing cross-tenant data access

---

# Database Collections

## users

Stores global account and profile data.

Fields:
* `id` - MongoDB ObjectId
* `username` - Unique (global)
* `displayName` - Search field
* `password` - Bcrypt hash
* `externalUserId` - Optional external system ID
* `createdAt` - Account creation timestamp

Indexes:
* `{username: 1}` - Unique

## tenant_members

Maps users to workspaces with roles.

Fields:
* `id` - MongoDB ObjectId
* `tenantId` - Workspace identifier
* `userId` - User identifier
* `role` - Role (OWNER, ADMIN, MEMBER)
* `joinedAt` - Timestamp

Indexes:
* `{tenantId: 1, userId: 1}` - Unique

## conversations

Stores direct and group metadata.

Fields:
* `id` - MongoDB ObjectId
* `tenantId` - Workspace identifier (scoped index)
* `type` - ConversationType enum: DM or GROUP
* `name` - Group name (null for DMs)
* `createdBy` - UserID of owner
* `createdAt` - Creation timestamp
* `participantIds` - Set of userIds

Indexes:
* `{tenantId: 1, participantIds: 1}` - Find conversations by participant
* `{tenantId: 1, createdAt: -1}` - Time-based queries

## messages

Stores conversation messages.

Fields:
* `id` - MongoDB ObjectId
* `tenantId` - Workspace identifier (scoped index)
* `conversationId` - Reference to conversation
* `senderId` - UserID of sender
* `content` - Message text
* `replyToId` - Optional message ID this replies to
* `createdAt` - Message timestamp
* `updatedAt` - Last edit timestamp (null if never edited)
* `edited` - Boolean flag
* `deleted` - Boolean flag (soft delete)
* `readBy` - Set of userIds who have read this message

Indexes:
* `{tenantId: 1, conversationId: 1, createdAt: -1}` - History queries

---

# Business Rules

## Conversations

* Users cannot create DMs with themselves
* Existing DMs are reused (no duplicate DM pairs)
* Only group owners can rename groups
* Only group owners can add or remove participants
* Owners cannot be removed (must transfer ownership first)
* Owners cannot leave without transferring ownership

## Messages

* Only conversation participants can send messages
* Empty messages are rejected
* Reply targets must belong to the same conversation
* Only senders can edit their own messages
* Deleted messages cannot be edited
* Messages are soft-deleted, never removed from database
* Message content is replaced with "This message was deleted" when soft-deleted
* Read tracking is per user, per message

---

# Error Responses

All APIs return consistent error structure:

```json
{
  "timestamp": "2026-05-10T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid request parameters"
}
```

Common HTTP status codes:

* `400` - Bad Request (validation, missing tenant header, invalid input)
* `401` - Unauthorized (invalid token, token tenant mismatch)
* `403` - Forbidden (not authorized for this resource)
* `404` - Not Found (resource does not exist or not accessible to this tenant)
* `500` - Internal Server Error (unexpected server error)

---

# Project Structure

```text
src/main/java/com/learnerview/chitchat/

config/
  SecurityConfig.java           - Spring Security configuration
  WebSocketConfig.java          - STOMP and WebSocket setup

controllers/
  AuthController.java           - Registration and login
  UserController.java           - User management APIs
  ConversationController.java   - Conversation APIs
  MessageController.java        - Message REST APIs
  RealtimeMessageController.java - WebSocket message handler
  WebhookController.java        - Webhook management

dto/
  AuthResponse.java             - Base auth response
  SaasAuthResponse.java         - Multi-tenant login response
  TenantInfo.java               - Workspace metadata for responses
  LoginRequest.java             - Login payload
  RegisterRequest.java          - Registration payload
  UserProfileResponse.java      - User profile response
  WorkspaceRequest.java         - Create/Update workspace payload
  SendMessageRequest.java       - Send message payload
  EditMessageRequest.java        - Edit message payload
  RealtimeMessageRequest.java   - WebSocket message payload
  GenerateInviteRequest.java    - Invite generation payload
  AcceptInviteRequest.java      - Invite acceptance payload
  ChangePasswordRequest.java    - Password change payload
  RegisterWebhookRequest.java   - Webhook registration payload

entities/
  User.java                     - User domain model
  Conversation.java             - Conversation domain model
  ConversationType.java         - Enum: DM, GROUP
  Message.java                  - Message domain model
  WebhookSubscription.java      - Webhook subscription

exception/
  GlobalExceptionHandler.java   - Centralized error handling

repositories/
  UserRepository.java           - MongoDB user queries
  ConversationRepository.java   - MongoDB conversation queries
  MessageRepository.java        - MongoDB message queries
  WebhookSubscriptionRepository.java - Webhook subscriptions

security/
  JwtTokenProvider.java         - JWT generation and validation
  JwtAuthenticationFilter.java  - JWT extraction and context setup
  WebSocketSubscriptionInterceptor.java - WebSocket authorization

service/
  UserService.java              - User domain contract
  ConversationService.java      - Conversation domain contract
  MessageService.java           - Message domain contract
  WebhookService.java           - Webhook domain contract
  EventPublisherService.java    - Webhook event publishing

service/impl/
  UserServiceImpl.java           - User business logic
  ConversationServiceImpl.java   - Conversation business logic
  MessageServiceImpl.java        - Message business logic
  WebhookServiceImpl.java        - Webhook business logic
  WebhookEventPublisherServiceImpl.java - Event publishing
  MongoUserDetailsService.java  - Spring Security user loading

tenant/
  TenantContext.java            - Request-scoped tenant storage
  TenantHeaderFilter.java       - Tenant header extraction
  WebSocketTenantHandshakeInterceptor.java - WebSocket tenant setup
```

---

# Webhook Events

Webhooks allow external systems to listen for events in ChitChat.

Supported events:

* `message.sent` - New message created
* `message.deleted` - Message soft-deleted
* `conversation.created` - New conversation created

## Event Payload

```json
{
  "tenantId": "acme-corp",
  "event": "message.sent",
  "timestamp": "2026-03-28T10:15:30",
  "data": {
    "messageId": "60d5ec49a1b2c3e4f5g6h7i8",
    "conversationId": "60d5ec49a1b2c3e4f5g6h7i9",
    "sender": "john",
    "content": "Hello everyone"
  }
}
```

## Webhook Security

Webhooks are signed with HMAC-SHA256:

```http
X-Signature: HMAC_SHA256(payload, secret)
```

Recipients should verify the signature using their registered secret before processing the event.

## Delivery

Webhooks are delivered asynchronously via HTTP POST to registered endpoints. Delivery failures are logged but do not block message persistence.

---

# License

MIT
