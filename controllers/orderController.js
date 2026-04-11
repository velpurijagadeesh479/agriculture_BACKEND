const pool = require('../config/db');

// POST /api/orders - Place order from cart
exports.placeOrder = async (req, res) => {
  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();

    const buyerId = req.user.id;
    const { shippingAddress } = req.body;

    // Get cart items
    const [cartItems] = await connection.query(
      `SELECT ci.*, p.price, p.quantity as stock, p.farmer_id, p.name, p.unit
       FROM cart_items ci
       JOIN products p ON ci.product_id = p.id
       WHERE ci.buyer_id = ?`,
      [buyerId]
    );

    if (cartItems.length === 0) {
      await connection.rollback();
      return res.status(400).json({ error: 'Cart is empty.' });
    }

    // Verify stock availability
    for (const item of cartItems) {
      if (item.quantity > item.stock) {
        await connection.rollback();
        return res.status(400).json({ error: `Insufficient stock for ${item.name}. Available: ${item.stock}` });
      }
    }

    // Calculate total
    const totalAmount = cartItems.reduce((sum, item) => sum + (item.price * item.quantity), 0);
    const shipping = 50;
    const tax = totalAmount * 0.18;
    const grandTotal = totalAmount + shipping + tax;

    // Create order
    const [orderResult] = await connection.query(
      'INSERT INTO orders (buyer_id, total_amount, shipping_address, status, payment_status) VALUES (?, ?, ?, ?, ?)',
      [buyerId, grandTotal, shippingAddress || 'Default Address', 'pending', 'paid']
    );
    const orderId = orderResult.insertId;

    // Create order items and reduce stock
    for (const item of cartItems) {
      await connection.query(
        'INSERT INTO order_items (order_id, product_id, farmer_id, quantity, price_at_purchase, unit) VALUES (?, ?, ?, ?, ?, ?)',
        [orderId, item.product_id, item.farmer_id, item.quantity, item.price, item.unit]
      );

      // Reduce product stock
      await connection.query(
        'UPDATE products SET quantity = quantity - ? WHERE id = ?',
        [item.quantity, item.product_id]
      );

      // Mark out_of_stock if quantity reaches 0
      await connection.query(
        'UPDATE products SET status = "out_of_stock" WHERE id = ? AND quantity <= 0',
        [item.product_id]
      );
    }

    // Clear cart
    await connection.query('DELETE FROM cart_items WHERE buyer_id = ?', [buyerId]);

    await connection.commit();

    res.status(201).json({
      message: 'Order placed successfully!',
      orderId,
      total: grandTotal.toFixed(2)
    });
  } catch (err) {
    await connection.rollback();
    console.error('Place order error:', err);
    res.status(500).json({ error: 'Server error while placing order.' });
  } finally {
    connection.release();
  }
};

// GET /api/orders/buyer - Buyer's orders
exports.getBuyerOrders = async (req, res) => {
  try {
    const [orders] = await pool.query(
      `SELECT o.*,
        (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.id) as item_count
       FROM orders o
       WHERE o.buyer_id = ?
       ORDER BY o.created_at DESC`,
      [req.user.id]
    );

    // Get items for each order
    for (let order of orders) {
      const [items] = await pool.query(
        `SELECT oi.*, p.name as product_name, p.category,
          u.name as farmer_name,
          (SELECT pi.image_url FROM product_images pi WHERE pi.product_id = oi.product_id AND pi.is_primary = 1 LIMIT 1) as image
         FROM order_items oi
         JOIN products p ON oi.product_id = p.id
         JOIN users u ON oi.farmer_id = u.id
         WHERE oi.order_id = ?`,
        [order.id]
      );
      order.items = items;
    }

    res.json({ orders });
  } catch (err) {
    console.error('Get buyer orders error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// GET /api/orders/farmer - Farmer's received orders
exports.getFarmerOrders = async (req, res) => {
  try {
    const [items] = await pool.query(
      `SELECT oi.*, o.status as order_status, o.created_at as order_date, o.payment_status,
        p.name as product_name, p.category,
        u.name as buyer_name, u.email as buyer_email, u.phone as buyer_phone,
        (SELECT pi.image_url FROM product_images pi WHERE pi.product_id = oi.product_id AND pi.is_primary = 1 LIMIT 1) as image
       FROM order_items oi
       JOIN orders o ON oi.order_id = o.id
       JOIN products p ON oi.product_id = p.id
       JOIN users u ON o.buyer_id = u.id
       WHERE oi.farmer_id = ?
       ORDER BY o.created_at DESC`,
      [req.user.id]
    );

    res.json({ orders: items });
  } catch (err) {
    console.error('Get farmer orders error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// GET /api/orders/:id - Get order details
exports.getOrderById = async (req, res) => {
  try {
    const [orders] = await pool.query(
      `SELECT o.*, u.name as buyer_name, u.email as buyer_email
       FROM orders o
       JOIN users u ON o.buyer_id = u.id
       WHERE o.id = ?`,
      [req.params.id]
    );

    if (orders.length === 0) return res.status(404).json({ error: 'Order not found.' });

    const order = orders[0];

    // Verify access
    if (order.buyer_id !== req.user.id && req.user.role !== 'admin') {
      // Check if farmer has items in this order
      const [farmerItems] = await pool.query(
        'SELECT id FROM order_items WHERE order_id = ? AND farmer_id = ?',
        [order.id, req.user.id]
      );
      if (farmerItems.length === 0) {
        return res.status(403).json({ error: 'Access denied.' });
      }
    }

    const [items] = await pool.query(
      `SELECT oi.*, p.name as product_name, p.category,
        u.name as farmer_name,
        (SELECT pi.image_url FROM product_images pi WHERE pi.product_id = oi.product_id AND pi.is_primary = 1 LIMIT 1) as image
       FROM order_items oi
       JOIN products p ON oi.product_id = p.id
       JOIN users u ON oi.farmer_id = u.id
       WHERE oi.order_id = ?`,
      [order.id]
    );

    order.items = items;
    res.json({ order });
  } catch (err) {
    console.error('Get order error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// PUT /api/orders/:id/status - Update order status
exports.updateOrderStatus = async (req, res) => {
  try {
    const { status } = req.body;
    const orderId = req.params.id;

    // Check permission
    const [orders] = await pool.query('SELECT buyer_id FROM orders WHERE id = ?', [orderId]);
    if (orders.length === 0) return res.status(404).json({ error: 'Order not found.' });

    if (req.user.role !== 'admin') {
      const [farmerItems] = await pool.query(
        'SELECT id FROM order_items WHERE order_id = ? AND farmer_id = ?',
        [orderId, req.user.id]
      );
      if (farmerItems.length === 0) {
        return res.status(403).json({ error: 'Not authorized.' });
      }
    }

    await pool.query('UPDATE orders SET status = ? WHERE id = ?', [status, orderId]);

    // Also update order items status
    if (req.user.role === 'admin') {
      await pool.query('UPDATE order_items SET status = ? WHERE order_id = ?', [status, orderId]);
    } else {
      await pool.query('UPDATE order_items SET status = ? WHERE order_id = ? AND farmer_id = ?', [status, orderId, req.user.id]);
    }

    res.json({ message: 'Order status updated.' });
  } catch (err) {
    console.error('Update order status error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// GET /api/orders/all - Admin: all orders
exports.getAllOrders = async (req, res) => {
  try {
    const [orders] = await pool.query(
      `SELECT o.*, u.name as buyer_name, u.email as buyer_email,
        (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.id) as item_count
       FROM orders o
       JOIN users u ON o.buyer_id = u.id
       ORDER BY o.created_at DESC`
    );

    res.json({ orders });
  } catch (err) {
    console.error('Get all orders error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};
