const express = require('express');
const router = express.Router();
const orderController = require('../controllers/orderController');
const { authenticateToken, authorizeRoles } = require('../middleware/auth');

router.use(authenticateToken);

router.post('/', authorizeRoles('buyer'), orderController.placeOrder);
router.get('/buyer', authorizeRoles('buyer'), orderController.getBuyerOrders);
router.get('/farmer', authorizeRoles('farmer'), orderController.getFarmerOrders);
router.get('/all', authorizeRoles('admin'), orderController.getAllOrders);
router.get('/:id', orderController.getOrderById);
router.put('/:id/status', authorizeRoles('farmer', 'admin'), orderController.updateOrderStatus);

module.exports = router;
