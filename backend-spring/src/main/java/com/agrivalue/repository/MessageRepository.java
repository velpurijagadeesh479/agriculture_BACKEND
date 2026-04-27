package com.agrivalue.repository;

import com.agrivalue.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Integer> {
    @Query("SELECT m FROM Message m WHERE (m.senderId = :userId AND m.receiverId = :otherUserId) OR (m.senderId = :otherUserId AND m.receiverId = :userId) ORDER BY m.createdAt ASC")
    List<Message> findConversation(Integer userId, Integer otherUserId);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true WHERE m.senderId = :senderId AND m.receiverId = :receiverId AND m.isRead = false")
    void markAsRead(Integer senderId, Integer receiverId);

    @Query(value = "SELECT m.* FROM messages m WHERE m.id IN (SELECT MAX(m2.id) FROM messages m2 WHERE m2.sender_id = :userId OR m2.receiver_id = :userId GROUP BY LEAST(m2.sender_id, m2.receiver_id), GREATEST(m2.sender_id, m2.receiver_id)) ORDER BY m.created_at DESC", nativeQuery = true)
    List<Message> findLatestConversations(Integer userId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.senderId = :senderId AND m.receiverId = :receiverId AND m.isRead = false")
    long countUnread(Integer senderId, Integer receiverId);
}
