const pool = require('../config/db');

// GET /api/admin/dashboard
exports.getDashboardStats = async (req, res) => {
  try {
    const [totalUsers] = await pool.query('SELECT COUNT(*) as count FROM users');
    const [activeFarmers] = await pool.query('SELECT COUNT(*) as count FROM users WHERE role = "farmer" AND status = "active"');
    const [activeBuyers] = await pool.query('SELECT COUNT(*) as count FROM users WHERE role = "buyer" AND status = "active"');
    const [totalProducts] = await pool.query('SELECT COUNT(*) as count FROM products');
    const [totalOrders] = await pool.query('SELECT COUNT(*) as count FROM orders');
    const [revenue] = await pool.query('SELECT COALESCE(SUM(total_amount), 0) as total FROM orders WHERE payment_status = "paid"');

    // Recent users
    const [recentUsers] = await pool.query(
      'SELECT id, name, email, role, status, created_at FROM users ORDER BY created_at DESC LIMIT 5'
    );

    // Recent orders
    const [recentOrders] = await pool.query(
      `SELECT o.*, u.name as buyer_name
       FROM orders o
       JOIN users u ON o.buyer_id = u.id
       ORDER BY o.created_at DESC LIMIT 5`
    );

    res.json({
      stats: {
        totalUsers: totalUsers[0].count,
        activeFarmers: activeFarmers[0].count,
        activeBuyers: activeBuyers[0].count,
        totalProducts: totalProducts[0].count,
        totalOrders: totalOrders[0].count,
        revenue: revenue[0].total
      },
      recentUsers,
      recentOrders
    });
  } catch (err) {
    console.error('Admin dashboard error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// GET /api/admin/users
exports.getAllUsers = async (req, res) => {
  try {
    const { search, role, status } = req.query;

    let query = 'SELECT id, name, email, role, phone, location, business_name, status, is_verified, created_at FROM users WHERE 1=1';
    const params = [];

    if (search) {
      query += ' AND (name LIKE ? OR email LIKE ?)';
      params.push(`%${search}%`, `%${search}%`);
    }
    if (role) { query += ' AND role = ?'; params.push(role); }
    if (status) { query += ' AND status = ?'; params.push(status); }

    query += ' ORDER BY created_at DESC';

    const [users] = await pool.query(query, params);
    res.json({ users });
  } catch (err) {
    console.error('Get all users error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// GET /api/admin/farmers
exports.getAllFarmers = async (req, res) => {
  try {
    const { search } = req.query;
    let query = `
      SELECT u.id, u.name, u.email, u.phone, u.location, u.business_name, u.status, u.created_at,
        (SELECT COUNT(*) FROM products p WHERE p.farmer_id = u.id) as product_count,
        (SELECT COUNT(*) FROM order_items oi WHERE oi.farmer_id = u.id) as order_count,
        (SELECT COALESCE(SUM(oi.price_at_purchase * oi.quantity), 0) FROM order_items oi JOIN orders o ON oi.order_id = o.id WHERE oi.farmer_id = u.id AND o.payment_status = 'paid') as total_earnings
      FROM users u WHERE u.role = 'farmer'
    `;
    const params = [];
    if (search) {
      query += ' AND (u.name LIKE ? OR u.email LIKE ?)';
      params.push(`%${search}%`, `%${search}%`);
    }
    query += ' ORDER BY u.created_at DESC';

    const [farmers] = await pool.query(query, params);
    res.json({ farmers });
  } catch (err) {
    console.error('Get all farmers error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// GET /api/admin/buyers
exports.getAllBuyers = async (req, res) => {
  try {
    const { search } = req.query;
    let query = `
      SELECT u.id, u.name, u.email, u.phone, u.location, u.business_name, u.status, u.created_at,
        (SELECT COUNT(*) FROM orders o WHERE o.buyer_id = u.id) as order_count,
        (SELECT COALESCE(SUM(o.total_amount), 0) FROM orders o WHERE o.buyer_id = u.id AND o.payment_status = 'paid') as total_spent
      FROM users u WHERE u.role = 'buyer'
    `;
    const params = [];
    if (search) {
      query += ' AND (u.name LIKE ? OR u.email LIKE ?)';
      params.push(`%${search}%`, `%${search}%`);
    }
    query += ' ORDER BY u.created_at DESC';

    const [buyers] = await pool.query(query, params);
    res.json({ buyers });
  } catch (err) {
    console.error('Get all buyers error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// GET /api/admin/transactions
exports.getTransactions = async (req, res) => {
  try {
    const { search, status } = req.query;
    let query = `
      SELECT o.*, u.name as buyer_name, u.email as buyer_email,
        (SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.id) as item_count
      FROM orders o
      JOIN users u ON o.buyer_id = u.id
      WHERE 1=1
    `;
    const params = [];
    if (search) {
      query += ' AND (u.name LIKE ? OR u.email LIKE ? OR o.id = ?)';
      params.push(`%${search}%`, `%${search}%`, search);
    }
    if (status && status !== 'All Status') {
      query += ' AND o.status = ?';
      params.push(status.toLowerCase());
    }
    query += ' ORDER BY o.created_at DESC';

    const [transactions] = await pool.query(query, params);
    res.json({ transactions });
  } catch (err) {
    console.error('Get transactions error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// GET /api/admin/analytics
exports.getAnalytics = async (req, res) => {
  try {
    // Monthly order trends (last 6 months)
    const [monthlyOrders] = await pool.query(`
      SELECT DATE_FORMAT(created_at, '%Y-%m') as month,
        COUNT(*) as orders,
        COALESCE(SUM(total_amount), 0) as revenue
      FROM orders
      WHERE created_at >= DATE_SUB(NOW(), INTERVAL 6 MONTH)
      GROUP BY DATE_FORMAT(created_at, '%Y-%m')
      ORDER BY month ASC
    `);

    // Category breakdown
    const [categoryBreakdown] = await pool.query(`
      SELECT p.category, COUNT(*) as product_count,
        COALESCE(SUM(oi.quantity * oi.price_at_purchase), 0) as revenue
      FROM products p
      LEFT JOIN order_items oi ON p.id = oi.product_id
      GROUP BY p.category
    `);

    // User growth (last 6 months)
    const [userGrowth] = await pool.query(`
      SELECT DATE_FORMAT(created_at, '%Y-%m') as month,
        SUM(CASE WHEN role = 'farmer' THEN 1 ELSE 0 END) as farmers,
        SUM(CASE WHEN role = 'buyer' THEN 1 ELSE 0 END) as buyers
      FROM users
      WHERE created_at >= DATE_SUB(NOW(), INTERVAL 6 MONTH)
      GROUP BY DATE_FORMAT(created_at, '%Y-%m')
      ORDER BY month ASC
    `);

    // Top products
    const [topProducts] = await pool.query(`
      SELECT p.name, p.category, COUNT(oi.id) as order_count,
        COALESCE(SUM(oi.quantity * oi.price_at_purchase), 0) as revenue
      FROM products p
      JOIN order_items oi ON p.id = oi.product_id
      GROUP BY p.id
      ORDER BY order_count DESC
      LIMIT 5
    `);

    res.json({
      monthlyOrders,
      categoryBreakdown,
      userGrowth,
      topProducts
    });
  } catch (err) {
    console.error('Analytics error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};

// PUT /api/admin/users/:id/status
exports.updateUserStatus = async (req, res) => {
  try {
    const { status } = req.body;
    await pool.query('UPDATE users SET status = ? WHERE id = ?', [status, req.params.id]);
    res.json({ message: 'User status updated.' });
  } catch (err) {
    console.error('Update user status error:', err);
    res.status(500).json({ error: 'Server error.' });
  }
};
