package com.learnerview.chitchat.conversation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationDirectKeyTest {

    @Test
    void directKeyIsOrderIndependent() {
        assertThat(Conversation.directKey("b", "a")).isEqualTo(Conversation.directKey("a", "b"));
    }

    @Test
    void directKeyUsesSortedUserIds() {
        assertThat(Conversation.directKey("user-2", "user-10")).isEqualTo("user-10:user-2");
    }

    @Test
    void directKeyRejectsSelfConversation() {
        assertThatThrownBy(() -> Conversation.directKey("a", "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void directKeyRejectsNullParticipant() {
        assertThatThrownBy(() -> Conversation.directKey(null, "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
