const pool = require('../config/db');

// POST /api/contact
exports.submitContact = async (req, res) => {
  try {
    const { name, email, subject, message } = req.body;

    await pool.query(
      'INSERT INTO contact_messages (name, email, subject, message) VALUES (?, ?, ?, ?)',
      [name, email, subject, message]
    );

    res.status(201).json({ message: 'Message saved successfully.' });
  } catch (err) {
    console.error('Contact submission error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};
