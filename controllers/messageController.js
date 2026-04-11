const pool = require('../config/db');

// GET /api/messages/conversations
exports.getConversations = async (req, res) => {
  try {
    const userId = req.user.id;

    const [conversations] = await pool.query(
      `SELECT 
        CASE WHEN m.sender_id = ? THEN m.receiver_id ELSE m.sender_id END as other_user_id,
        u.name as other_user_name, u.role as other_user_role, u.profile_photo,
        m.content as last_message,
        m.created_at as last_message_time,
        (SELECT COUNT(*) FROM messages m2 WHERE m2.sender_id = CASE WHEN m.sender_id = ? THEN m.receiver_id ELSE m.sender_id END AND m2.receiver_id = ? AND m2.is_read = 0) as unread_count
       FROM messages m
       JOIN users u ON u.id = CASE WHEN m.sender_id = ? THEN m.receiver_id ELSE m.sender_id END
       WHERE m.id IN (
         SELECT MAX(id) FROM messages
         WHERE sender_id = ? OR receiver_id = ?
         GROUP BY LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id)
       )
       ORDER BY m.created_at DESC`,
      [userId, userId, userId, userId, userId, userId]
    );

    res.json({ conversations });
  } catch (err) {
    console.error('Get conversations error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// GET /api/messages/:userId
exports.getMessages = async (req, res) => {
  try {
    const userId = req.user.id;
    const otherUserId = req.params.userId;

    const [messages] = await pool.query(
      `SELECT m.*, u.name as sender_name
       FROM messages m
       JOIN users u ON m.sender_id = u.id
       WHERE (m.sender_id = ? AND m.receiver_id = ?) OR (m.sender_id = ? AND m.receiver_id = ?)
       ORDER BY m.created_at ASC`,
      [userId, otherUserId, otherUserId, userId]
    );

    // Mark messages as read
    await pool.query(
      'UPDATE messages SET is_read = 1 WHERE sender_id = ? AND receiver_id = ? AND is_read = 0',
      [otherUserId, userId]
    );

    res.json({ messages });
  } catch (err) {
    console.error('Get messages error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// POST /api/messages
exports.sendMessage = async (req, res) => {
  try {
    const { receiverId, content } = req.body;
    const senderId = req.user.id;

    // Check receiver exists
    const [users] = await pool.query('SELECT id FROM users WHERE id = ?', [receiverId]);
    if (users.length === 0) return res.status(404).json({ error: 'Recipient not found.' });

    const [result] = await pool.query(
      'INSERT INTO messages (sender_id, receiver_id, content) VALUES (?, ?, ?)',
      [senderId, receiverId, content]
    );

    res.status(201).json({
      message: 'Message sent.',
      data: {
        id: result.insertId,
        sender_id: senderId,
        receiver_id: receiverId,
        content,
        sender_name: req.user.name,
        is_read: 0,
        created_at: new Date()
      }
    });
  } catch (err) {
    console.error('Send message error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};
