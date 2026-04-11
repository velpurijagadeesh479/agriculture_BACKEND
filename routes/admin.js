const express = require('express');
const router = express.Router();
const adminController = require('../controllers/adminController');
const { authenticateToken, authorizeRoles } = require('../middleware/auth');

router.use(authenticateToken, authorizeRoles('admin'));

router.get('/dashboard', adminController.getDashboardStats);
router.get('/users', adminController.getAllUsers);
router.get('/farmers', adminController.getAllFarmers);
router.get('/buyers', adminController.getAllBuyers);
router.get('/transactions', adminController.getTransactions);
router.get('/analytics', adminController.getAnalytics);
router.put('/users/:id/status', adminController.updateUserStatus);

module.exports = router;
