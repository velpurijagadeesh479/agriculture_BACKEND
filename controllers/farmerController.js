const pool = require('../config/db');

// GET /api/farmer/dashboard
exports.getDashboardStats = async (req, res) => {
  try {
    const farmerId = req.user.id;

    const [totalProducts] = await pool.query('SELECT COUNT(*) as count FROM products WHERE farmer_id = ?', [farmerId]);
    const [activeProducts] = await pool.query('SELECT COUNT(*) as count FROM products WHERE farmer_id = ? AND status = "active"', [farmerId]);
    const [totalOrders] = await pool.query(
      'SELECT COUNT(*) as count FROM order_items WHERE farmer_id = ?', [farmerId]
    );
    const [pendingOrders] = await pool.query(
      'SELECT COUNT(*) as count FROM order_items WHERE farmer_id = ? AND status = "pending"', [farmerId]
    );
    const [earnings] = await pool.query(
      `SELECT COALESCE(SUM(oi.price_at_purchase * oi.quantity), 0) as total
       FROM order_items oi
       JOIN orders o ON oi.order_id = o.id
       WHERE oi.farmer_id = ? AND o.payment_status = 'paid'`,
      [farmerId]
    );

    // Recent orders
    const [recentOrders] = await pool.query(
      `SELECT oi.*, o.created_at as order_date, o.status as order_status,
        p.name as product_name, u.name as buyer_name
       FROM order_items oi
       JOIN orders o ON oi.order_id = o.id
       JOIN products p ON oi.product_id = p.id
       JOIN users u ON o.buyer_id = u.id
       WHERE oi.farmer_id = ?
       ORDER BY o.created_at DESC LIMIT 5`,
      [farmerId]
    );

    res.json({
      stats: {
        totalProducts: totalProducts[0].count,
        activeProducts: activeProducts[0].count,
        totalOrders: totalOrders[0].count,
        pendingOrders: pendingOrders[0].count,
        totalEarnings: earnings[0].total
      },
      recentOrders
    });
  } catch (err) {
    console.error('Farmer dashboard error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// GET /api/farmer/inventory
exports.getInventory = async (req, res) => {
  try {
    const farmerId = req.user.id;

    const [products] = await pool.query(
      `SELECT p.*,
        (SELECT pi.image_url FROM product_images pi WHERE pi.product_id = p.id AND pi.is_primary = 1 LIMIT 1) as image
       FROM products p
       WHERE p.farmer_id = ?
       ORDER BY p.quantity ASC`,
      [farmerId]
    );

    const totalItems = products.length;
    const lowStock = products.filter(p => p.quantity > 0 && p.quantity <= 10).length;
    const outOfStock = products.filter(p => p.quantity <= 0).length;

    res.json({
      stats: { totalItems, lowStock, outOfStock },
      products
    });
  } catch (err) {
    console.error('Get inventory error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// GET /api/farmer/earnings
exports.getEarnings = async (req, res) => {
  try {
    const farmerId = req.user.id;

    // Total earnings
    const [total] = await pool.query(
      `SELECT COALESCE(SUM(oi.price_at_purchase * oi.quantity), 0) as total
       FROM order_items oi
       JOIN orders o ON oi.order_id = o.id
       WHERE oi.farmer_id = ? AND o.payment_status = 'paid'`,
      [farmerId]
    );

    // This month
    const [thisMonth] = await pool.query(
      `SELECT COALESCE(SUM(oi.price_at_purchase * oi.quantity), 0) as total
       FROM order_items oi
       JOIN orders o ON oi.order_id = o.id
       WHERE oi.farmer_id = ? AND o.payment_status = 'paid'
       AND MONTH(o.created_at) = MONTH(NOW()) AND YEAR(o.created_at) = YEAR(NOW())`,
      [farmerId]
    );

    // Pending
    const [pending] = await pool.query(
      `SELECT COALESCE(SUM(oi.price_at_purchase * oi.quantity), 0) as total
       FROM order_items oi
       JOIN orders o ON oi.order_id = o.id
       WHERE oi.farmer_id = ? AND o.payment_status = 'pending'`,
      [farmerId]
    );

    // Recent transactions
    const [transactions] = await pool.query(
      `SELECT oi.*, o.created_at as order_date, o.payment_status,
        p.name as product_name, u.name as buyer_name,
        (oi.price_at_purchase * oi.quantity) as amount
       FROM order_items oi
       JOIN orders o ON oi.order_id = o.id
       JOIN products p ON oi.product_id = p.id
       JOIN users u ON o.buyer_id = u.id
       WHERE oi.farmer_id = ?
       ORDER BY o.created_at DESC LIMIT 10`,
      [farmerId]
    );

    // Monthly earnings (last 6 months)
    const [monthlyEarnings] = await pool.query(
      `SELECT DATE_FORMAT(o.created_at, '%Y-%m') as month,
        COALESCE(SUM(oi.price_at_purchase * oi.quantity), 0) as earnings
       FROM order_items oi
       JOIN orders o ON oi.order_id = o.id
       WHERE oi.farmer_id = ? AND o.payment_status = 'paid'
       AND o.created_at >= DATE_SUB(NOW(), INTERVAL 6 MONTH)
       GROUP BY DATE_FORMAT(o.created_at, '%Y-%m')
       ORDER BY month ASC`,
      [farmerId]
    );

    res.json({
      stats: {
        totalEarnings: total[0].total,
        thisMonth: thisMonth[0].total,
        pending: pending[0].total
      },
      transactions,
      monthlyEarnings
    });
  } catch (err) {
    console.error('Get earnings error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};
