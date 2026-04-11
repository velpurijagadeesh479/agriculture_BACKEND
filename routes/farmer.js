const express = require('express');
const router = express.Router();
const farmerController = require('../controllers/farmerController');
const { authenticateToken, authorizeRoles } = require('../middleware/auth');

router.use(authenticateToken, authorizeRoles('farmer'));

router.get('/dashboard', farmerController.getDashboardStats);
router.get('/inventory', farmerController.getInventory);
router.get('/earnings', farmerController.getEarnings);

module.exports = router;
