const express = require('express');
const router = express.Router();
const messageController = require('../controllers/messageController');
const { authenticateToken } = require('../middleware/auth');

router.use(authenticateToken);

router.get('/conversations', messageController.getConversations);
router.get('/:userId', messageController.getMessages);
router.post('/', messageController.sendMessage);

module.exports = router;
